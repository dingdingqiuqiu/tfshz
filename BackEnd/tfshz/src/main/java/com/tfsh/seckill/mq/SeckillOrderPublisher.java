package com.tfsh.seckill.mq;

/**
 * 秒杀订单消息发送器。
 *
 * 两种实现（用 seckill.mq 切换，默认 kafka）：
 *   - kafka：KafkaSeckillOrderPublisher，分区顺序 + 消费组横向扩展 + DLQ
 *   - stream：RedisStreamSeckillOrderPublisher，复用 Redis，无需额外中间件
 *
 * 接口只表达"把一条订单消息投出去"，具体 MQ 对上层不可见。
 */
public interface SeckillOrderPublisher {

    /**
     * 发送订单消息。
     *
     * @return true 表示已确认投递成功；false 表示投递失败（调用方必须回滚预扣库存）
     */
    boolean publish(SeckillOrderMessage message);
}
