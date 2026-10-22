package com.tfsh.pay;

/**
 * 支付通道抽象 —— 这是留给「真实支付」的接口，业务代码只依赖它。
 *
 * 商品订单、秒杀券订单共用这一个抽象：差别只体现在 PayRequest 的金额/描述里。
 *
 * 接真实支付时要做的三件事（已预留入口）：
 *   1. prepay()：向第三方「统一下单」，把 prepay_id / 支付参数返回给前端；
 *   2. 回调：第三方异步通知 → PayController#notify → 订单 Service 的 confirmPaid 做 CAS 改状态；
 *   3. 幂等 + 验签：回调必须校验签名，并用 orderId + CAS 保证重复回调不会重复处理。
 */
public interface PayService {

    /** 渠道名 */
    String channel();

    /** 预下单：返回前端唤起支付所需参数 */
    PayResult prepay(PayRequest request);

    /**
     * 查询支付是否成功（真实场景：主动查单，避免回调丢失）。
     * 演示实现直接返回 true。
     */
    boolean queryPaid(String tradeNo);
}
