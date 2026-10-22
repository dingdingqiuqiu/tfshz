package com.tfsh.pay;

import lombok.Data;

/**
 * 统一的支付入参：商品订单和秒杀券订单都走这里，支付通道不需要知道"买的是什么"。
 */
@Data
public class PayRequest {

    /** 订单号（两种订单各自的主键） */
    private Long orderId;

    /** 金额，单位：分 */
    private Integer amount;

    /** 商品描述（账单展示用） */
    private String subject;

    public static PayRequest of(Long orderId, Integer amount, String subject) {
        PayRequest request = new PayRequest();
        request.setOrderId(orderId);
        request.setAmount(amount == null ? 0 : amount);
        request.setSubject(subject);
        return request;
    }
}
