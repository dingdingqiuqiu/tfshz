package com.tfsh.seckill.mq;

import com.tfsh.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 消息 ↔ 订单 的相互转换。
 * 放在一处，Kafka 监听器和 Redis Stream 消费者共用，避免两套解析逻辑漂移。
 */
@Slf4j
@Component
public class SeckillOrderMessageCodec {

    public SeckillOrderMessage fromOrder(VoucherOrder order) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(order.getId());
        message.setUserId(order.getUserId());
        message.setVoucherId(order.getVoucherId());
        message.setCreatedAt(System.currentTimeMillis());
        return message;
    }

    public VoucherOrder toOrder(SeckillOrderMessage message) {
        VoucherOrder order = new VoucherOrder();
        order.setId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setVoucherId(message.getVoucherId());
        order.setPayType(1);
        order.setStatus(1);
        order.setVersion(0);
        LocalDateTime createTime = message.getCreatedAt() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(message.getCreatedAt()), ZoneId.systemDefault());
        order.setCreateTime(createTime);
        order.setUpdateTime(createTime);
        return order;
    }

    /** 拆流消息字段（Redis Stream 用，Kafka 直接反序列化 JSON） */
    public SeckillOrderMessage fromStreamFields(java.util.Map<String, String> body) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        try {
            message.setOrderId(Long.valueOf(body.get("orderId")));
            message.setUserId(Long.valueOf(body.get("userId")));
            message.setVoucherId(Long.valueOf(body.get("voucherId")));
            String ts = body.get("createTime");
            message.setCreatedAt(ts == null ? System.currentTimeMillis() : Long.parseLong(ts));
            return message;
        } catch (Exception e) {
            log.error("[seckill] 消息字段解析失败：{}", body, e);
            return null;
        }
    }
}
