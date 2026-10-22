package com.tfsh.dto;

import lombok.Data;

import java.util.List;

/**
 * 下单请求：一次可以买多个商品。
 * 例：{"shopId":1,"items":[{"productId":1,"quantity":2},{"productId":7,"quantity":1}]}
 */
@Data
public class ProductOrderForm {

    private Long shopId;

    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
