package com.tfsh.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tfsh.dto.Result;
import com.tfsh.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /** 秒杀下单：Redis 预扣库存 + 订单消息入队，订单异步落库 */
    Result seckillVoucher(Long voucherId);

    /** 查询订单落库结果（异步链路，可能还没落库） */
    Result queryOrder(Long orderId);

    /** 我的优惠券列表（订单 + 券 + 店铺） */
    Result queryMyOrders();
}
