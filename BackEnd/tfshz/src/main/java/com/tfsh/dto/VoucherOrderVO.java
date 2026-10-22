package com.tfsh.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的优惠券 / 券订单详情」返回体：订单 + 券 + 店铺。
 *
 * 与商品订单的 ProductOrderVO 保持同一套「订单生命周期」字段，
 * 这样前端的订单卡片可以完全复用（支付 / 二维码 / 核销）。
 *
 * orderId 特意用 String：订单号是 19 位 snowflake 风格数字，超过 JS Number 的安全整数范围，
 * 直接以 number 返回给前端会丢精度。
 */
@Data
public class VoucherOrderVO {

    private String orderId;

    private Long voucherId;

    private String title;

    private String subTitle;

    private Long shopId;

    private String shopName;

    /** 支付金额，单位：分 */
    private Long payValue;

    /** 抵扣金额，单位：分 */
    private Long actualValue;

    /** 1 待支付 / 2 已支付 / 3 已核销 / 4 已关闭 */
    private Integer status;

    private String statusText;

    /** 核销码（二维码内容） */
    private String verifyCode;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime verifyTime;

    private LocalDateTime createTime;
}
