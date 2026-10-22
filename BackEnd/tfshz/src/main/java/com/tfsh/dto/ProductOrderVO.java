package com.tfsh.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品订单返回体。
 *
 * orderId 用 String：19 位订单号超过 JS Number 的安全整数范围，直接给 number 会丢精度。
 */
@Data
public class ProductOrderVO {

    private String orderId;

    private Long shopId;

    private String shopName;

    /** 单位：分 */
    private Integer totalAmount;

    /** 1 待支付 / 2 待核销 / 3 已核销 / 4 已关闭 */
    private Integer status;

    private String statusText;

    /** 核销码（二维码内容），只在待核销/已核销时有意义 */
    private String verifyCode;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime verifyTime;

    private LocalDateTime createTime;

    private List<ItemVO> items;

    @Data
    public static class ItemVO {

        private String productName;

        /** 单位：分 */
        private Integer price;

        private Integer quantity;

        /** 单位：分 */
        private Integer amount;

        public ItemVO(String productName, Integer price, Integer quantity) {
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.amount = (price == null ? 0 : price) * (quantity == null ? 0 : quantity);
        }
    }
}
