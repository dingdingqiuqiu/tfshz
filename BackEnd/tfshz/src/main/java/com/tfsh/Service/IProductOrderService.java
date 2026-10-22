package com.tfsh.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tfsh.dto.ProductOrderForm;
import com.tfsh.dto.Result;
import com.tfsh.entity.ProductOrder;

import java.util.List;

public interface IProductOrderService extends IService<ProductOrder> {

    /** 下单：校验商品、条件更新扣库存、生成订单（待支付）与核销码 */
    Result createOrder(ProductOrderForm form);

    /** 支付（演示通道）：预下单 → 确认回调 → CAS 置为已支付 */
    Result payOrder(Long orderId);

    /** 用户主动取消未支付订单（CAS + 回补库存） */
    Result cancelOrder(Long orderId);

    /** 订单详情（只能看自己的） */
    Result queryOrder(Long orderId);

    /** 我的订单列表 */
    Result queryMyOrders();

    /** 商家核销：核销码 → 已支付订单置为已核销（CAS，防重复核销） */
    Result verify(String code);

    /**
     * 支付结果确认（预留给第三方异步回调）。
     * 幂等：已支付/已核销直接返回成功；已关闭返回失败。
     */
    Result confirmPaidByNotify(Long orderId, String tradeNo);

    /** 查询已超时仍未支付的订单（定时任务用） */
    List<ProductOrder> listExpired(int limit);

    /** 关闭单笔订单并回补库存（独立事务，由定时任务逐条调用） */
    boolean closeOrder(Long orderId);
}
