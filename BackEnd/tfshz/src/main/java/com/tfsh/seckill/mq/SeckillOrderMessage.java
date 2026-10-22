package com.tfsh.seckill.mq;

import lombok.Data;

/**
 * 秒杀订单消息：MQ 里只放"重建订单所需的最小字段"，不放整张订单。
 *
 * 为什么不直接序列化 VoucherOrder：
 *   1. 消息体积越小，Kafka 吞吐越高、重试越便宜；
 *   2. 消息格式与数据库字段解耦，加字段不会让老消息无法反序列化。
 */
@Data
public class SeckillOrderMessage {

    private Long orderId;

    private Long userId;

    private Long voucherId;

    /** 下单时间戳（毫秒），消费端据此还原 createTime */
    private Long createdAt;
}
