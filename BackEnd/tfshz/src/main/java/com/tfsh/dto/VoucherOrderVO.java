package com.tfsh.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * “我的优惠券”列表项：订单 + 券 + 店铺的最小展示集合。
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

    private Long payValue;

    private Long actualValue;

    /** 1：未支付；2：已支付；3：已核销；4：已取消 */
    private Integer status;

    private LocalDateTime createTime;
}
