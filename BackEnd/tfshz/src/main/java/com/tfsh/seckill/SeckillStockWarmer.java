package com.tfsh.seckill;

import com.tfsh.Service.ISeckillVoucherService;
import com.tfsh.entity.SeckillVoucher;
import com.tfsh.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀库存预热。
 *
 * 为什么必须有预热：
 *   Lua 里第一步是 GET 库存 key。key 不存在时 GET 返回 false，tonumber(false) 得到 nil，
 *   接下来任何比较都会抛脚本错误 —— 也就是说“没预热”等于“秒杀直接 500”。
 *
 * 两条预热入口：
 *   1. 应用启动时把 DB 里所有秒杀券刷进 Redis（ApplicationRunner）；
 *   2. 请求打进来时如果发现 key 不在（比如 Redis 被清过），懒加载一次再重试。
 *
 * 幂等要点：库存 key 已存在时 **不覆盖**，否则重启一次就会把运行中已经扣减的库存重置回 DB 值。
 */
@Slf4j
@Component
public class SeckillStockWarmer implements ApplicationRunner {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        warmUpAll();
    }

    public void warmUpAll() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        if (vouchers == null || vouchers.isEmpty()) {
            log.info("[seckill] 没有需要预热的秒杀券");
            return;
        }
        vouchers.forEach(this::warmUp);
        log.info("[seckill] 库存预热完成，共 {} 张秒杀券", vouchers.size());
    }

    /** 把一张券的库存与活动时间写进 Redis；库存 key 已存在则跳过，避免覆盖运行态 */
    public void warmUp(SeckillVoucher voucher) {
        if (voucher == null || voucher.getVoucherId() == null) {
            return;
        }
        Long voucherId = voucher.getVoucherId();
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;

        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey)) && voucher.getStock() != null) {
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
            log.info("[seckill] 券 {} 库存预热：{}", voucherId, voucher.getStock());
        }

        Map<String, String> meta = new HashMap<>(2);
        if (voucher.getBeginTime() != null) {
            meta.put("begin", String.valueOf(toEpochMilli(voucher.getBeginTime())));
        }
        if (voucher.getEndTime() != null) {
            meta.put("end", String.valueOf(toEpochMilli(voucher.getEndTime())));
        }
        if (!meta.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(RedisConstants.SECKILL_META_KEY + voucherId, meta);
        }
    }

    /**
     * 懒预热：库存 key 不在时从 DB 拉一次。
     *
     * @return true 表示预热后 Redis 里有库存 key，可以继续跑 Lua
     */
    public boolean warmUpIfAbsent(Long voucherId) {
        if (voucherId == null) {
            return false;
        }
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            return true;
        }
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return false;
        }
        warmUp(voucher);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey));
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
