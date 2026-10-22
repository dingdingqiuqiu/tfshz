package com.tfsh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品订单。
 *
 * status 生命周期：1 待支付 → 2 已支付 → 3 已核销；未支付可被用户取消或被定时任务关闭（4）。
 *
 * version 是 CAS 乐观锁版本号：支付、取消、核销、超时关单四条路径都可能同时改同一行，
 * 每次更新都带 `where status = 期望值 and version = 读到的值`，保证只有一个能成功。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_product_order")
public class ProductOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long shopId;

    /** 订单总额，单位：分 */
    private Integer totalAmount;

    /** 1 待支付 / 2 已支付 / 3 已核销 / 4 已关闭 */
    private Integer status;

    /** 核销码，同时作为二维码内容 */
    private String verifyCode;

    /** CAS 版本号 */
    private Integer version;

    /** 支付截止时间，超过后由定时任务关单 */
    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime verifyTime;

    private LocalDateTime closeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
