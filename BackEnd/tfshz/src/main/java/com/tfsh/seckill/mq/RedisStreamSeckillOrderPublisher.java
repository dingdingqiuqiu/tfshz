package com.tfsh.seckill.mq;

import com.tfsh.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis Stream 版订单消息发送器（`seckill.mq=stream` 时启用）。
 *
 * 与 Kafka 版的差别：不需要额外中间件，但 Stream 的分区/横向扩展能力弱于 Kafka，
 * 消息积压时也会挤占同一个 Redis 的内存（与缓存、锁共享资源）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.mq", havingValue = "stream")
public class RedisStreamSeckillOrderPublisher implements SeckillOrderPublisher {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean publish(SeckillOrderMessage message) {
        try {
            stringRedisTemplate.opsForStream().add(StreamRecords.newRecord()
                    .ofMap(Map.of(
                            "orderId", String.valueOf(message.getOrderId()),
                            "userId", String.valueOf(message.getUserId()),
                            "voucherId", String.valueOf(message.getVoucherId()),
                            "createTime", String.valueOf(message.getCreatedAt())))
                    .withStreamKey(RedisConstants.SECKILL_ORDER_STREAM));
            return true;
        } catch (Exception e) {
            log.error("[seckill][stream] 订单消息投递失败 orderId={}", message.getOrderId(), e);
            return false;
        }
    }
}
