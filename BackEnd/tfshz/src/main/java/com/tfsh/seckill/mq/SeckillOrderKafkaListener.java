package com.tfsh.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfsh.config.KafkaConfig;
import com.tfsh.seckill.SeckillOrderProcessor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消费者（Kafka）。
 *
 * 幂等与重试的边界：
 *   - 正常返回 → 容器（AckMode.RECORD）提交这条 offset；
 *   - 抛**可重试**异常（如 SeckillRetryLaterException：抢锁超时 / DB 抖动）→ offset 不提交，
 *     DefaultErrorHandler 按 1s/2s/4s 重试 3 次，仍失败则转发死信 topic 并提交 offset；
 *   - 抛**不可重试**异常（消息体格式错误、字段缺失）→ 直接进死信，不浪费重试；
 *   - 业务失败（DB 库存不足 / 用户重复下单）不是异常：回滚 Redis 预扣 + 写死信 topic，然后正常返回（提交 offset）。
 *
 * 重复消费是允许发生的（at-least-once），幂等由 SeckillOrderPersistService 保证：
 *   orderId 已存在直接返回、DB 条件扣减 stock>0、唯一索引 uk_user_voucher。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.mq", havingValue = "kafka", matchIfMissing = true)
public class SeckillOrderKafkaListener {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SeckillOrderProcessor processor;

    @Resource
    private SeckillOrderMessageCodec codec;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_SECKILL_ORDER,
            groupId = "${seckill.kafka.group:seckill-order-group}",
            containerFactory = "seckillListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        SeckillOrderMessage message;
        try {
            message = objectMapper.readValue(record.value(), SeckillOrderMessage.class);
        } catch (Exception e) {
            // 抛 IllegalArgumentException：配置里声明为不可重试 → 直接进死信
            throw new IllegalArgumentException("订单消息格式错误: " + record.value(), e);
        }
        if (message.getOrderId() == null || message.getUserId() == null || message.getVoucherId() == null) {
            throw new IllegalArgumentException("订单消息字段缺失: " + record.value());
        }

        log.info("[seckill][kafka] 消费订单消息 partition={} offset={} orderId={}",
                record.partition(), record.offset(), message.getOrderId());
        // 可重试异常直接抛出，交给 ErrorHandler 重试 / 死信
        processor.process(codec.toOrder(message));
    }
}
