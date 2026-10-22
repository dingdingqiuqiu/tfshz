package com.tfsh.seckill;

import com.tfsh.utils.RedisConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 订单消息消费者（Redis Stream 消费者组）。
 *
 * 分工：
 *   - 主消费：StreamMessageListenerContainer 用 XREADGROUP ... > 阻塞拉新消息，**手动 ACK**；
 *   - 兜底消费：定时任务扫 Pending List（XACK 之前就崩掉的消息都在这里），
 *     空闲超过阈值用 XCLAIM 认领回来重试，投递次数超限进死信。
 *
 * 为什么不能只靠“>”读新消息：
 *   “>”只投递从未投递过的消息。消费者拿到消息后如果进程崩了/处理抛异常没 ACK，
 *   这条消息就永远留在这条 consumer 的 PEL 里，新消息继续来，它就再也不会被读到 ——
 *   所以必须有一个扫 PEL 的补偿任务，这也是“消息不丢”的关键。
 */
@Slf4j
@Component
public class SeckillOrderStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillOrderProcessor processor;

    @Resource
    private RedisConnectionFactory connectionFactory;

    /** 消费者名必须每个实例唯一：同名消费者会互相“抢”对方的 PEL 记账 */
    private final String consumerName = buildConsumerName();

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    private ScheduledExecutorService pendingReclaimer;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @PostConstruct
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ensureConsumerGroup();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(16)
                        .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(RedisConstants.SECKILL_ORDER_GROUP, consumerName),
                StreamOffset.create(RedisConstants.SECKILL_ORDER_STREAM, ReadOffset.lastConsumed()),
                this);
        container.start();

        pendingReclaimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "seckill-pending-reclaimer");
            thread.setDaemon(true);
            return thread;
        });
        // 每 30s 扫一次 Pending List；认领阈值 60s，保证不会抢走正在处理的消息
        pendingReclaimer.scheduleWithFixedDelay(this::reclaimIdlePending, 30, 30, TimeUnit.SECONDS);

        log.info("[seckill] 消费者已启动 consumer={} group={} stream={}",
                consumerName, RedisConstants.SECKILL_ORDER_GROUP, RedisConstants.SECKILL_ORDER_STREAM);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
        if (pendingReclaimer != null) {
            pendingReclaimer.shutdownNow();
        }
        log.info("[seckill] 消费者已停止 consumer={}", consumerName);
    }

    /** 主链路：新消息到达。首次投递计数为 1 */
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        processor.handle(message.getId(), toStringMap(message.getValue()), 1L);
    }

    /**
     * 扫 Pending List 并认领空闲消息。
     *
     * XCLAIM 会把消息的所有权转给当前消费者并重置空闲时间，
     * 这样多实例部署时不会有两个实例同时处理同一条消息。
     */
    private void reclaimIdlePending() {
        try {
            PendingMessages pending = stringRedisTemplate.opsForStream()
                    .pending(RedisConstants.SECKILL_ORDER_STREAM, RedisConstants.SECKILL_ORDER_GROUP,
                            Range.unbounded(), 100);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().getSeconds() < RedisConstants.SECKILL_PENDING_IDLE_SECONDS) {
                    continue;
                }
                List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream()
                        .claim(RedisConstants.SECKILL_ORDER_STREAM, RedisConstants.SECKILL_ORDER_GROUP, consumerName,
                                Duration.ofSeconds(RedisConstants.SECKILL_PENDING_IDLE_SECONDS), message.getId());
                if (claimed == null || claimed.isEmpty()) {
                    continue;
                }
                long deliveryCount = message.getTotalDeliveryCount() + 1;
                for (MapRecord<String, Object, Object> record : claimed) {
                    log.warn("[seckill] 认领 pending 消息 id={} 第 {} 次投递", record.getId(), deliveryCount);
                    processor.handle(record.getId(), toStringMap(record.getValue()), deliveryCount);
                }
            }
        } catch (Exception e) {
            log.error("[seckill] 处理 Pending List 失败", e);
        }
    }

    /** Redis 里取出来的字段统一转成 String，避免 Object/String 泛型在两条读取路径上不一致 */
    private static Map<String, String> toStringMap(Map<?, ?> value) {
        Map<String, String> body = new HashMap<>();
        if (value == null) {
            return body;
        }
        value.forEach((k, v) -> body.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        return body;
    }

    /**
     * 建消费者组。流不存在时 XGROUP CREATE 会报错，所以先塞一条占位消息把流“造”出来，
     * 建完组再从 0 号位开始消费并删掉占位消息（不会干扰业务解析）。
     */
    private void ensureConsumerGroup() {
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstants.SECKILL_ORDER_STREAM))) {
                RecordId bootstrap = stringRedisTemplate.opsForStream().add(StreamRecords.newRecord()
                        .ofMap(Map.of("bootstrap", "1"))
                        .withStreamKey(RedisConstants.SECKILL_ORDER_STREAM));
                stringRedisTemplate.opsForStream().createGroup(
                        RedisConstants.SECKILL_ORDER_STREAM, ReadOffset.from("0"), RedisConstants.SECKILL_ORDER_GROUP);
                if (bootstrap != null) {
                    stringRedisTemplate.opsForStream().delete(RedisConstants.SECKILL_ORDER_STREAM, bootstrap);
                }
                return;
            }
            stringRedisTemplate.opsForStream().createGroup(
                    RedisConstants.SECKILL_ORDER_STREAM, ReadOffset.from("0"), RedisConstants.SECKILL_ORDER_GROUP);
            log.info("[seckill] 消费者组创建成功 group={}", RedisConstants.SECKILL_ORDER_GROUP);
        } catch (Exception e) {
            // BUSYGROUP：组已存在，正常情况
            log.info("[seckill] 消费者组已存在或创建失败：{}", e.getMessage());
        }
    }

    private static String buildConsumerName() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return "seckill-" + host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
