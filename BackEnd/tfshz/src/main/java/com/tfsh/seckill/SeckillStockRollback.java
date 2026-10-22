package com.tfsh.seckill;

import com.tfsh.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀预扣回滚（Redis 侧）：库存 +1、把用户从「已下单集合」里移除。
 * 抽成组件是因为异步落库失败、用户取消、超时关单三条路径都要用。
 */
@Slf4j
@Component
public class SeckillStockRollback {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("lua/seckill_rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void rollback(Long voucherId, Long userId) {
        if (voucherId == null || userId == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(ROLLBACK_SCRIPT,
                    List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId,
                            RedisConstants.SECKILL_ORDER_KEY + voucherId),
                    userId.toString());
        } catch (Exception e) {
            log.error("[seckill] 回滚预扣库存失败 voucherId={} userId={}", voucherId, userId, e);
        }
    }

    /**
     * 幂等回滚：同一个 orderId 只回滚一次。
     *
     * 为什么需要：Kafka 是 at-least-once，业务失败（如 DB 库存不足）回滚后如果提交 offset 前崩溃，
     * 消息会被重投、再次触发回滚。没有守卫的话 INCR 会执行多次，Redis 库存虚高。
     * 用 SETNX + 24h TTL 做一次性标记，比"查库判断"便宜得多。
     */
    public void rollbackOnce(Long orderId, Long voucherId, Long userId) {
        if (orderId == null) {
            rollback(voucherId, userId);
            return;
        }
        try {
            String guardKey = RedisConstants.SECKILL_ROLLBACK_KEY + orderId;
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(guardKey, "1", Duration.ofHours(24));
            if (!Boolean.TRUE.equals(first)) {
                log.info("[seckill] 预扣已回滚过，跳过 orderId={}", orderId);
                return;
            }
            rollback(voucherId, userId);
        } catch (Exception e) {
            log.error("[seckill] 幂等回滚失败 orderId={}", orderId, e);
        }
    }
}
