package com.tfsh.controller;

import com.tfsh.dto.Result;
import com.tfsh.order.OrderVerifyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一核销台接口：商品订单与秒杀券订单共用。
 * 核销码带前缀（P/V），服务端据此定位到正确的订单表。
 */
@RestController
@RequestMapping("/order")
public class OrderVerifyController {

    @Resource
    private OrderVerifyService orderVerifyService;

    @PostMapping("verify/{code}")
    public Result verify(@PathVariable("code") String code) {
        return orderVerifyService.verify(code);
    }
}
