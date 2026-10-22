package com.tfsh.controller;

import com.tfsh.Service.IProductOrderService;
import com.tfsh.dto.ProductOrderForm;
import com.tfsh.dto.Result;
import com.tfsh.order.OrderVerifyService;
import com.tfsh.task.OrderTimeoutTask;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品订单：下单 → 支付（演示）→ 核销，以及与定时关单配套的演示触发接口。
 * 这些接口都需要登录（LoginInterceptor 未放开）。
 */
@RestController
@RequestMapping("/product-order")
public class ProductOrderController {

    @Resource
    private IProductOrderService productOrderService;

    @Resource
    private OrderTimeoutTask orderTimeoutTask;

    @Resource
    private OrderVerifyService orderVerifyService;

    /** 下单：返回待支付订单（含核销码） */
    @PostMapping
    public Result createOrder(@RequestBody ProductOrderForm form) {
        return productOrderService.createOrder(form);
    }

    /** 我的订单 */
    @GetMapping("list")
    public Result queryMyOrders() {
        return productOrderService.queryMyOrders();
    }

    /** 订单详情（支付页 / 核销码页用） */
    @GetMapping("{orderId}")
    public Result queryOrder(@PathVariable("orderId") Long orderId) {
        return productOrderService.queryOrder(orderId);
    }

    /** 支付：走 PayService（当前是 Mock 通道，不调任何第三方 API） */
    @PostMapping("{orderId}/pay")
    public Result pay(@PathVariable("orderId") Long orderId) {
        return productOrderService.payOrder(orderId);
    }

    /** 取消未支付订单（CAS + 回补库存） */
    @PostMapping("{orderId}/cancel")
    public Result cancel(@PathVariable("orderId") Long orderId) {
        return productOrderService.cancelOrder(orderId);
    }

    /**
     * 核销（兼容旧地址）：实际交给统一核销入口，商品单和秒杀券单都能核销。
     * 新前端统一走 POST /order/verify/{code}。
     */
    @PostMapping("verify/{code}")
    public Result verify(@PathVariable("code") String code) {
        return orderVerifyService.verify(code);
    }

    /**
     * 手动触发一次「超时关单」（演示/运维用，等价于定时任务跑一轮）。
     * 商品订单与秒杀券订单都会被扫到，返回本轮成功关闭的总单数。
     */
    @PostMapping("task/close-expired")
    public Result closeExpiredOnce() {
        return Result.ok(orderTimeoutTask.closeExpiredOnce());
    }
}
