package com.tfsh.controller;

import cn.hutool.core.util.StrUtil;
import com.tfsh.Service.IProductOrderService;
import com.tfsh.Service.IVoucherOrderService;
import com.tfsh.dto.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付回调入口（预留）。
 *
 * 真实接入第三方支付时，这里会被微信/支付宝的异步通知调用，需要补三件事：
 *   1. 验签（用渠道公钥/证书校验通知签名），失败直接拒；
 *   2. 幂等（同一笔回调可能被推送多次）—— confirmPaid 内部用 CAS 保证只处理一次；
 *   3. 把 /pay/notify 加进登录白名单（现在仍要求登录，避免被随意刷接口）。
 *
 * 商品订单和秒杀券订单共用这个回调入口，用 body 里的 type 区分（product / voucher）。
 */
@Slf4j
@RestController
@RequestMapping("/pay")
public class PayController {

    @Resource
    private IProductOrderService productOrderService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("notify")
    public Result notify(@RequestBody Map<String, String> body) {
        String orderIdText = body == null ? null : body.get("orderId");
        String tradeNo = body == null ? null : body.get("tradeNo");
        String type = body == null ? null : body.get("type");
        if (StrUtil.isBlank(orderIdText)) {
            return Result.fail("缺少 orderId");
        }
        Long orderId = Long.valueOf(orderIdText);
        log.info("[pay][notify] 收到支付回调 type={} orderId={} tradeNo={}", type, orderId, tradeNo);

        if ("voucher".equalsIgnoreCase(type)) {
            return voucherOrderService.confirmPaid(orderId, tradeNo);
        }
        return productOrderService.confirmPaidByNotify(orderId, tradeNo);
    }
}
