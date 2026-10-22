package com.tfsh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单明细：下单时把商品名和单价「快照」下来，
 * 之后商家改价/改名都不影响历史订单的展示与对账。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_product_order_item")
public class ProductOrderItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /** 下单时的商品名快照 */
    private String productName;

    /** 下单时的单价快照，单位：分 */
    private Integer price;

    private Integer quantity;
}
