package com.tfsh.pay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 演示用支付通道：不调任何第三方 API，预下单直接返回成功，
 * 由订单 Service 走「回调确认」同一条路径把订单置为已支付。
 *
 * 想换成真支付：新建一个实现 PayService 的类（如 WxPayService），
 * 把 prepay() 换成统一下单、queryPaid() 换成查单即可，订单侧代码不用改。
 */
@Slf4j
@Service
public class MockPayService implements PayService {

    @Override
    public String channel() {
        return "mock";
    }

    @Override
    public PayResult prepay(PayRequest request) {
        PayResult result = new PayResult();
        result.setChannel(channel());
        result.setMock(true);
        result.setTradeNo("MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        Map<String, String> params = new HashMap<>();
        params.put("orderId", String.valueOf(request.getOrderId()));
        params.put("amount", String.valueOf(request.getAmount()));
        params.put("subject", request.getSubject());
        params.put("mockPay", "true");
        result.setPayParams(params);
        log.info("[pay][mock] 预下单 orderId={} amount={}分 subject={} tradeNo={}",
                request.getOrderId(), request.getAmount(), request.getSubject(), result.getTradeNo());
        return result;
    }

    @Override
    public boolean queryPaid(String tradeNo) {
        // 演示环境：预下单即视为支付成功；真实实现要在这里查单
        return true;
    }
}
