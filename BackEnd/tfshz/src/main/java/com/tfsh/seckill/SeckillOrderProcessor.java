package com.tfsh.seckill;

import com.tfsh.entity.VoucherOrder;
import com.tfsh.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * 单条订单消息的处理者：解析 -> 落库 -> 决定 ACK / 回滚 / 死信。
 *
 * 处理结果与后续动作的对应关系：
 *   SUCCESS / ALREADY_EXISTS          -> XACK，消息生命周期结束
 *   USER_DUPLICATE / STOCK_EXHAUSTED  -> 回滚 Redis 预扣 + 进死信 + XACK（业务失败，重试也没用）
 *   可重试异常（抢锁超时、DB 抖动）      -> **不 XACK**，留在 Pending List 等认领重试
 *   重试次数超过 SECKILL_MAX_DELIVERY  -> 回滚 + 死信 + XACK，避免毒消息把队列堵死
 */
@Slf4j
@Component
public class SeckillOrderProcessor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillOrderPersistService persistService;

    @Resource
    private SeckillStockRollback stockRollback;

    /**
     * @param recordId      流消息 ID（用于 XACK / 死信回填）
     * @param body          消息字段
     * @param deliveryCount 已经投递了几次（首次为 1）
     */
    public void handle(RecordId recordId, Map<String, String> body, long deliveryCount) {
        VoucherOrder order = parse(body);
        if (order == null) {
            deadLetter(recordId, body, "消息字段缺失或格式错误");
            acknowledge(recordId);
            return;
        }

        try {
            PersistOutcome outcome = persistService.persist(order);
            switch (outcome) {
                case SUCCESS -> {
                    log.info("[seckill] 订单落库成功 orderId={} userId={} voucherId={}",
                            order.getId(), order.getUserId(), order.getVoucherId());
                    acknowledge(recordId);
                }
                case ALREADY_EXISTS -> {
                    log.info("[seckill] 订单已存在，幂等跳过 orderId={}", order.getId());
                    acknowledge(recordId);
                }
                case USER_DUPLICATE -> {
                    // 用户其实已经买过了：这笔预扣是多余的，退回库存后收尾
                    rollbackStock(order);
                    deadLetter(recordId, body, "用户已购买过，预扣库存已回滚");
                    acknowledge(recordId);
                }
                case STOCK_EXHAUSTED -> {
                    rollbackStock(order);
                    deadLetter(recordId, body, "DB 库存不足（Redis 预扣与 DB 不同步），预扣库存已回滚");
                    acknowledge(recordId);
                }
                default -> {
                    deadLetter(recordId, body, "消息无效");
                    acknowledge(recordId);
                }
            }
        } catch (Exception e) {
            // 包括 SeckillRetryLaterException：都是“暂时不行、等会儿再来”的语义
            handleRetryable(recordId, body, order, deliveryCount, e);
        }
    }

    private void handleRetryable(RecordId recordId, Map<String, String> body,
                                 VoucherOrder order, long deliveryCount, Exception e) {
        if (deliveryCount >= RedisConstants.SECKILL_MAX_DELIVERY) {
            log.error("[seckill] 消息重试 {} 次仍失败，转死信 orderId={}", deliveryCount, order.getId(), e);
            rollbackStock(order);
            deadLetter(recordId, body, "重试耗尽：" + e.getMessage());
            acknowledge(recordId);
            return;
        }
        // 不 ACK：消息留在 Pending List，认领任务空闲超时后会重新 XCLAIM 再来一次
        log.warn("[seckill] 订单处理失败（第 {} 次），留待重试 orderId={} cause={}",
                deliveryCount, order.getId(), e.toString());
    }

    private VoucherOrder parse(Map<String, String> body) {
        try {
            VoucherOrder order = new VoucherOrder();
            order.setId(Long.valueOf(body.get("orderId")));
            order.setUserId(Long.valueOf(body.get("userId")));
            order.setVoucherId(Long.valueOf(body.get("voucherId")));
            String ts = body.get("createTime");
            LocalDateTime createTime = ts == null
                    ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(ts)), ZoneId.systemDefault());
            order.setCreateTime(createTime);
            order.setUpdateTime(createTime);
            order.setPayType(1);
            order.setStatus(1);
            return order;
        } catch (Exception e) {
            log.error("[seckill] 订单消息解析失败：{}", body, e);
            return null;
        }
    }

    /** 回滚预扣：库存 +1、用户从“已下单集合”里移除（Lua 里两步原子完成） */
    private void rollbackStock(VoucherOrder order) {
        stockRollback.rollback(order.getVoucherId(), order.getUserId());
    }

    private void acknowledge(RecordId recordId) {
        try {
            stringRedisTemplate.opsForStream()
                    .acknowledge(RedisConstants.SECKILL_ORDER_STREAM, RedisConstants.SECKILL_ORDER_GROUP, recordId);
        } catch (Exception e) {
            // ACK 失败也不要紧：消息还在 pending-list，认领任务会再处理一次（幂等保证不出错单）
            log.error("[seckill] XACK 失败 id={}", recordId, e);
        }
    }

    /** 死信：保留原始字段 + 失败原因，方便人工对账/补偿 */
    private void deadLetter(RecordId recordId, Map<String, String> body, String reason) {
        try {
            Map<String, String> message = new HashMap<>(body);
            message.put("recordId", String.valueOf(recordId));
            message.put("reason", reason);
            message.put("failedAt", String.valueOf(System.currentTimeMillis()));
            stringRedisTemplate.opsForStream().add(StreamRecords.newRecord()
                    .ofMap(message)
                    .withStreamKey(RedisConstants.SECKILL_ORDER_DLQ));
            log.warn("[seckill] 消息进入死信队列 id={} reason={}", recordId, reason);
        } catch (Exception e) {
            log.error("[seckill] 写死信队列失败 id={}", recordId, e);
        }
    }
}
