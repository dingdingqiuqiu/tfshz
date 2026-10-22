package com.tfsh.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfsh.config.KafkaConfig;
import com.tfsh.entity.VoucherOrder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务失败死信：DB 库存不足、用户重复下单这类"消息没问题但业务上做不了"的结果，
 * 不抛异常（重试也没用），而是投到死信 topic 供人工对账/补偿。
 *
 * 注意与"技术失败死信"的区别：
 *   - 技术失败（重试耗尽）由 DefaultErrorHandler 自动转发，消息是原始的；
 *   - 业务失败由这里转发，额外带上原因，便于排查。
 */
@Slf4j
@Component
public class SeckillDeadLetterPublisher {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private ObjectMapper objectMapper;

    public void send(VoucherOrder order, String reason) {
        Map<String, Object> payload = new HashMap<>(6);
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("voucherId", order.getVoucherId());
        payload.put("reason", reason);
        payload.put("failedAt", System.currentTimeMillis());
        try {
            kafkaTemplate.send(KafkaConfig.TOPIC_SECKILL_DLQ,
                    String.valueOf(order.getId()), objectMapper.writeValueAsString(payload));
            log.warn("[seckill][dlq] 订单进入死信队列 orderId={} reason={}", order.getId(), reason);
        } catch (Exception e) {
            // 死信都发不出去时只能靠日志兜底（生产上应接告警）
            log.error("[seckill][dlq] 死信投递失败 orderId={} reason={}", order.getId(), reason, e);
        }
    }
}
