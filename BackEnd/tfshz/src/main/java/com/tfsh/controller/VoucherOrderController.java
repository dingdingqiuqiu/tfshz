package com.tfsh.controller;

import com.tfsh.Service.IVoucherOrderService;
import com.tfsh.dto.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀下单：同步只返回“是否抢到资格 + 订单号”，订单落库由 Redis Stream 消费者异步完成。
     * 前端拿到 orderId 后可以轮询 GET /voucher-order/{orderId} 看落库结果。
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /** 我的优惠券列表（“我的 → 我的优惠券”） */
    @GetMapping("list")
    public Result queryMyOrders() {
        return voucherOrderService.queryMyOrders();
    }

    /** 查询订单（需要登录，且只能查自己的） */
    @GetMapping("{orderId}")
    public Result queryOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.queryOrder(orderId);
    }
}
