package com.tfsh.order;

import com.tfsh.Service.IProductService;
import com.tfsh.Service.ISeckillVoucherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 库存读写原语（商品 + 秒杀券）。
 *
 * 为什么要单独抽出来：
 *   1. **条件扣减只写一次**：`stock >= n` 的判断必须和扣减在**同一条 SQL** 里，
 *      散落在各个 Service 里迟早有人漏掉 `where stock > 0`；
 *   2. 让业务代码可测：业务层只依赖这几个布尔方法，单测时 mock 掉即可，
 *      不用去模拟 MyBatis-Plus 的链式 Wrapper；
 *   3. 语义清晰：deduct 是"抢到资格"，restore 是"退回"，调用点一眼能看出方向。
 */
@Slf4j
@Component
public class StockService {

    @Resource
    private IProductService productService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 商品库存条件扣减：库存在同一条 SQL 里判断并扣减，不会超卖。
     *
     * @return false 表示库存不足（并发下被别的请求扣完了）
     */
    public boolean deductProduct(Long productId, int quantity) {
        return productService.update()
                .setSql("stock = stock - " + quantity)
                .eq("id", productId)
                .ge("stock", quantity)
                .update();
    }

    /** 商品库存回补（订单取消 / 超时关单） */
    public boolean restoreProduct(Long productId, int quantity) {
        return productService.update()
                .setSql("stock = stock + " + quantity)
                .eq("id", productId)
                .update();
    }

    /**
     * 秒杀券库存条件扣减：`stock > 0` 与扣减在同一条 SQL，Redis 预扣算错也不会超卖。
     */
    public boolean deductSeckillVoucher(Long voucherId) {
        return seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
    }

    /** 秒杀券库存回补（用户取消 / 超时关单） */
    public boolean restoreSeckillVoucher(Long voucherId) {
        return seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", voucherId)
                .update();
    }
}
