package com.tfsh.order;

import cn.hutool.core.util.StrUtil;
import com.tfsh.Service.IProductOrderService;
import com.tfsh.Service.IVoucherOrderService;
import com.tfsh.dto.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 统一核销入口：商品订单和秒杀券订单共用同一个核销台。
 *
 * 定位方式：核销码带类型前缀（P 商品 / V 券），所以一条 SQL 就能定位到该查哪张表；
 * 历史上没有前缀的码退回"两张表都找一次"，兼容老数据。
 */
@Slf4j
@Service
public class OrderVerifyService {

    @Resource
    private IProductOrderService productOrderService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    public Result verify(String code) {
        if (StrUtil.isBlank(code)) {
            return Result.fail("请输入核销码");
        }
        String normalized = code.trim().toUpperCase();
        char prefix = normalized.charAt(0);

        if (prefix == VerifyCodeGenerator.PREFIX_PRODUCT) {
            return productOrderService.verify(normalized);
        }
        if (prefix == VerifyCodeGenerator.PREFIX_VOUCHER) {
            return voucherOrderService.verifyByCode(normalized);
        }

        // 兼容历史无前缀的码：先查商品订单，再查券订单；都失败时返回商品侧的结论
        Result productResult = productOrderService.verify(normalized);
        if (Boolean.TRUE.equals(productResult.getSuccess())) {
            return productResult;
        }
        Result voucherResult = voucherOrderService.verifyByCode(normalized);
        if (Boolean.TRUE.equals(voucherResult.getSuccess())) {
            return voucherResult;
        }
        log.info("[verify] 未匹配到任何订单 code={} productMsg={} voucherMsg={}",
                normalized, productResult.getErrorMsg(), voucherResult.getErrorMsg());
        return productResult;
    }
}
