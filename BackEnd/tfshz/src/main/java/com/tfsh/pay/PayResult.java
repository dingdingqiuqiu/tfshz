package com.tfsh.pay;

import lombok.Data;

import java.util.Map;

/**
 * 预下单结果：真实支付场景里，前端拿 payParams 去唤起微信/支付宝收银台。
 * 演示环境（Mock）只返回一个内部流水号，mock=true。
 */
@Data
public class PayResult {

    /** 支付渠道：mock / wxpay / alipay ... */
    private String channel;

    /** 支付流水号（回调时用来对账） */
    private String tradeNo;

    /** 是否演示支付 */
    private boolean mock;

    /** 未来接真实支付时，这里放第三方的支付参数（prepay_id、paySign 等） */
    private Map<String, String> payParams;
}
