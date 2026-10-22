package com.tfsh.seckill;

import com.tfsh.entity.VoucherOrder;
import com.tfsh.seckill.mq.SeckillOrderMessage;
import com.tfsh.seckill.mq.SeckillOrderMessageCodec;
import com.tfsh.utils.RedisConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Redis Stream 消费者（`seckill.mq=stream` 时的备选通道，Kafka 不可用可切回）。
 *
 * 与 Kafka 版的差异（也是默认选 Kafka 的理由）：
 *   - Stream 的 PEL 重试要自己写 XCLAIM 扫描任务，Kafka 由 ErrorHandler + DLQ 原生支持；
 *   - Stream 没有真正的分区横向扩展，消息积压会占用同一个 Redis 的内存；
 *   - 优点是不用额外中间件，故障面更小。
 *
 * 两者共用的部分：消息解析（SeckillOrderMessageCodec）、业务处理（SeckillOrderProcessor）、
 * 幂等（落库侧 orderId 去重 + 条件扣减 + 唯一索引）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.mq", havingValue = "stream")
public class SeckillOrderStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillOrderProcessor processor;

    @Resource
    private SeckillOrderMessageCodec codec;

    @Resource
    private RedisConnectionFactory connectionFactory;

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
        pendingReclaimer.scheduleWithFixedDelay(this::reclaimIdlePending, 30, 30, TimeUnit.SECONDS);
        log.info("[seckill][stream] 消费者已启动 consumer={} group={}",
                consumerName, RedisConstants.SECKILL_ORDER_GROUP);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
        if (pendingReclaimer != null) {
            pendingReclaimer.shutdownNow();
        }
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        handle(message.getId(), toStringMap(message.getValue()), 1L);
    }

    private void handle(RecordId recordId, Map<String, String> body, long deliveryCount) {
        SeckillOrderMessage msg = codec.fromStreamFields(body);
        if (msg == null) {
            acknowledge(recordId);      // 脏消息：直接丢弃并 ACK，避免堵住 PEL
            return;
        }
        VoucherOrder order = codec.toOrder(msg);
        try {
            processor.process(order);
            acknowledge(recordId);      // 业务成功（含"业务失败已回滚并入死信"）都算消费完成
        } catch (Exception e) {
            // 可重试失败：不 ACK，留在 PEL，由认领任务重投
            log.warn("[seckill][stream] 处理失败（第 {} 次），留待重试 orderId={}", deliveryCount, order.getId(), e);
        }
    }

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
                    handle(record.getId(), toStringMap(record.getValue()), deliveryCount);
                }
            }
        } catch (Exception e) {
            log.error("[seckill][stream] 处理 Pending List 失败", e);
        }
    }

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
        } catch (Exception e) {
            log.info("[seckill][stream] 消费者组已存在或创建失败：{}", e.getMessage());
        }
    }

    private void acknowledge(RecordId recordId) {
        try {
            stringRedisTemplate.opsForStream()
                    .acknowledge(RedisConstants.SECKILL_ORDER_STREAM, RedisConstants.SECKILL_ORDER_GROUP, recordId);
        } catch (Exception e) {
            log.error("[seckill][stream] XACK 失败 id={}", recordId, e);
        }
    }

    private static Map<String, String> toStringMap(Map<?, ?> value) {
        Map<String, String> body = new HashMap<>();
        if (value == null) {
            return body;
        }
        value.forEach((k, v) -> body.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        return body;
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
