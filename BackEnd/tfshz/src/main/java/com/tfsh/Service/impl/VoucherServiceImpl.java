package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.ISeckillVoucherService;
import com.tfsh.Service.IVoucherService;
import com.tfsh.dto.Result;
import com.tfsh.entity.SeckillVoucher;
import com.tfsh.entity.Voucher;
import com.tfsh.mapper.VoucherMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 新增 import
import cn.hutool.core.lang.TypeReference;
import com.tfsh.utils.CacheClient;
import com.tfsh.utils.CacheDeleteCompensator;

import java.util.concurrent.TimeUnit;

import static com.tfsh.utils.RedisConstants.CACHE_VOUCHER_KEY;
import static com.tfsh.utils.RedisConstants.CACHE_VOUCHER_TTL;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private CacheDeleteCompensator cacheDeleteCompensator;

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private com.tfsh.seckill.SeckillStockWarmer seckillStockWarmer;

/*
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }
*/

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 多级缓存：本地 Caffeine(5s) -> Redis 逻辑过期(30min) -> MySQL，逐级回填
        // 秒杀券是"更新频率极低、访问频率极高"的热点数据：击穿防护 + 热 Key 降压都在这一条链路上
        List<Voucher> vouchers = cacheClient.queryWithMultiLevel(
                CACHE_VOUCHER_KEY, shopId,
                new TypeReference<List<Voucher>>() {}.getType(),
                id -> getBaseMapper().queryVoucherOfShop(id),
                CACHE_VOUCHER_TTL, TimeUnit.MINUTES);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 预热秒杀库存与活动时间窗（库存 + meta 一起写，Lua 才能做时间窗校验）
        seckillStockWarmer.warmUp(seckillVoucher);
        // 新增秒杀券后删除该店铺的券列表缓存（失败自动补偿重试；本地缓存靠 5s TTL 自愈）
        cacheDeleteCompensator.delete(CACHE_VOUCHER_KEY + voucher.getShopId());
    }
}
