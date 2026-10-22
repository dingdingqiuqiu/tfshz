package com.tfsh.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tfsh.Service.ISeckillVoucherService;
import com.tfsh.entity.VoucherOrder;
import com.tfsh.mapper.VoucherOrderMapper;
import com.tfsh.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单落库（异步链路的最后一环，也是防超卖的第二道闸门）。
 *
 * 三层保护，任何一层单独失效都不会超卖：
 *   ① 幂等：orderId 已存在直接算成功（重复投递/重试都安全）；
 *   ② Redisson 锁 lock:order:{voucherId}:{userId}：把“同一用户对同一张券”的落库串行化，
 *      避免两个并发消息同时通过“查历史订单”的检查；
 *   ③ 条件更新 stock = stock - 1 WHERE stock > 0：数据库层面的原子扣减，
 *      即使 Redis 预扣算错了、消息重复了、锁失效了，也不可能把库存扣成负数；
 *      再加唯一索引 uk_user_voucher 兜住“一人一单”的最后一道口子。
 */
@Slf4j
@Service
public class SeckillOrderPersistService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    public PersistOutcome persist(VoucherOrder order) {
        if (order == null || order.getId() == null || order.getUserId() == null || order.getVoucherId() == null) {
            return PersistOutcome.INVALID;
        }
        Long orderId = order.getId();
        Long userId = order.getUserId();
        Long voucherId = order.getVoucherId();

        // ① 幂等：消息可能被重复投递（消费者崩溃、pending 重投），落过库就直接收工
        if (voucherOrderMapper.selectById(orderId) != null) {
            return PersistOutcome.ALREADY_EXISTS;
        }

        // ② 一人一单：Redisson 锁把同一用户同一张券的落库串行化
        RLock lock = redissonClient.getLock(RedisConstants.SECKILL_LOCK_KEY + voucherId + ":" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if (!locked) {
                // 抢不到锁属于瞬时失败：抛可重试异常，消息留在 pending-list 等下一轮
                throw new SeckillRetryLaterException("获取下单锁超时 voucherId=" + voucherId + " userId=" + userId);
            }

            Long history = voucherOrderMapper.selectCount(Wrappers.<VoucherOrder>lambdaQuery()
                    .eq(VoucherOrder::getUserId, userId)
                    .eq(VoucherOrder::getVoucherId, voucherId));
            if (history != null && history > 0) {
                return PersistOutcome.USER_DUPLICATE;
            }

            // ③ 条件更新：库存 > 0 才扣，原子且不会超卖
            boolean deducted = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!deducted) {
                return PersistOutcome.STOCK_EXHAUSTED;
            }

            try {
                voucherOrderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 唯一索引 uk_user_voucher 兜住了并发重复：库存扣减必须一起回滚
                log.warn("[seckill] 唯一索引拦下重复订单 userId={} voucherId={}", userId, voucherId);
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return PersistOutcome.USER_DUPLICATE;
            }
            return PersistOutcome.SUCCESS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SeckillRetryLaterException("获取下单锁被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
