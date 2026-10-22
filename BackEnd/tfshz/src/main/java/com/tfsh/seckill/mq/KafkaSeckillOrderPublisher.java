package com.tfsh.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfsh.config.KafkaConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 版订单消息发送器。
 *
 * 关键点：
 *   - **分区 key 用 userId**：同一用户的消息落到同一分区（顺序有保证），同时避免用 voucherId 造成热点分区；
 *   - producer 侧 `acks=all + enable.idempotence=true`：broker 确认 + 幂等生产，避免网络重试造成重复消息；
 *   - `get(3s)` 同步等待 broker ack：只有确认投递成功才向用户返回"抢到"，
 *     失败则由调用方回滚 Redis 预扣 —— 这是"预扣在 Redis、入队在 Java"的代价（对比 Stream 的 XADD 同脚本原子）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.mq", havingValue = "kafka", matchIfMissing = true)
public class KafkaSeckillOrderPublisher implements SeckillOrderPublisher {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean publish(SeckillOrderMessage message) {
        String key = String.valueOf(message.getUserId());
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(KafkaConfig.TOPIC_SECKILL_ORDER, key, payload)
                    .get(3, TimeUnit.SECONDS);
            log.info("[seckill][kafka] 订单消息已投递 orderId={} key={}", message.getOrderId(), key);
            return true;
        } catch (Exception e) {
            log.error("[seckill][kafka] 订单消息投递失败 orderId={}", message.getOrderId(), e);
            return false;
        }
    }
}
