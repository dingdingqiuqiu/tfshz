package com.tfsh.seckill;

import com.tfsh.entity.VoucherOrder;
import com.tfsh.seckill.mq.SeckillDeadLetterPublisher;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 单条订单消息的业务处理：落库（幂等 + 条件扣减 + 唯一索引），失败按类型分流。
 *
 * 这里是 MQ 无关的纯业务逻辑：
 *   - SUCCESS / ALREADY_EXISTS        → 正常返回（消费端提交 offset）
 *   - USER_DUPLICATE / STOCK_EXHAUSTED → 回滚 Redis 预扣（**幂等回滚**）+ 写业务死信，然后正常返回
 *   - 可重试异常（抢锁超时、DB 抖动）    → 抛出，由消费端重试 / 死信
 *
 * 为什么回滚要"幂等"：Kafka 是 at-least-once，回滚后如果提交 offset 前崩溃，
 * 消息会被重投，若无脑再 INCR 一次，Redis 库存就会虚高 —— 所以用 orderId 做一次性守卫。
 */
@Slf4j
@Component
public class SeckillOrderProcessor {

    private static final String DLQ_REASON_DUPLICATE = "用户已购买过，预扣库存已回滚";
    private static final String DLQ_REASON_STOCK = "DB 库存不足（Redis 预扣与 DB 不同步），预扣库存已回滚";

    @Resource
    private SeckillOrderPersistService persistService;

    @Resource
    private SeckillStockRollback stockRollback;

    @Resource
    private SeckillDeadLetterPublisher deadLetterPublisher;

    public void process(VoucherOrder order) {
        PersistOutcome outcome = persistService.persist(order);
        switch (outcome) {
            case SUCCESS -> log.info("[seckill] 订单落库成功 orderId={} userId={} voucherId={}",
                    order.getId(), order.getUserId(), order.getVoucherId());
            case ALREADY_EXISTS -> log.info("[seckill] 订单已存在，幂等跳过 orderId={}", order.getId());
            case USER_DUPLICATE -> {
                stockRollback.rollbackOnce(order.getId(), order.getVoucherId(), order.getUserId());
                deadLetterPublisher.send(order, DLQ_REASON_DUPLICATE);
            }
            case STOCK_EXHAUSTED -> {
                stockRollback.rollbackOnce(order.getId(), order.getVoucherId(), order.getUserId());
                deadLetterPublisher.send(order, DLQ_REASON_STOCK);
            }
            default -> {
                deadLetterPublisher.send(order, "消息字段缺失或格式错误");
                throw new IllegalArgumentException("订单消息无效，已转入死信 orderId=" + order.getId());
            }
        }
    }
}
