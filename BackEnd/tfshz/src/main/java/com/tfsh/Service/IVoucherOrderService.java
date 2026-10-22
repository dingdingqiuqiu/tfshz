package com.tfsh.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tfsh.dto.Result;
import com.tfsh.entity.VoucherOrder;

import java.util.List;

/**
 * 秒杀券订单。除了「抢券」这一步走 Redis + Stream，
 * 之后的支付、取消、超时关单、核销与商品订单完全同构（共用 PayService 与版本号 CAS）。
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /** 秒杀下单：Redis 预扣库存 + 订单消息入队，订单异步落库 */
    Result seckillVoucher(Long voucherId);

    /** 订单详情（异步落库前可能查不到，返回 null） */
    Result queryOrder(Long orderId);

    /** 我的券订单列表（订单 + 券 + 店铺） */
    Result queryMyOrders();

    /** 支付（演示通道）：预下单 → 查单 → CAS 置为已支付 */
    Result payOrder(Long orderId);

    /** 支付确认（支付回调/主动查单后调用，幂等） */
    Result confirmPaid(Long orderId, String tradeNo);

    /** 用户取消未支付订单（CAS + 回补库存） */
    Result cancelOrder(Long orderId);

    /** 关闭单笔订单并回补库存（独立事务，定时任务逐条调用） */
    boolean closeOrder(Long orderId);

    /** 查询已超时仍未支付的券订单（定时任务用） */
    List<VoucherOrder> listExpired(int limit);

    /** 商家核销：核销码 → 已支付订单置为已核销（CAS，防重复核销） */
    Result verifyByCode(String code);
}
