package com.tfsh.seckill;

import com.tfsh.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

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
}
