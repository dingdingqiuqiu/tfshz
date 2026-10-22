package com.tfsh.task;

import com.tfsh.Service.IProductOrderService;
import com.tfsh.Service.IVoucherOrderService;
import com.tfsh.entity.ProductOrder;
import com.tfsh.entity.VoucherOrder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单生命周期任务：定时关闭「超时未支付」的订单并回补库存。
 *
 * 覆盖两种订单：商品订单（tb_product_order）与秒杀券订单（tb_voucher_order）——
 * 两者的订单生命周期已经统一，关单逻辑完全同构，只是回补的库存落在不同的表/Redis key 上。
 *
 * 关单与支付的并发由「版本号 CAS」兜底：
 *   CAS 成功 → 订单确实被关闭 → 才回补库存；
 *   CAS 失败 → 说明这一单刚被支付 → 跳过，绝不回补库存。
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    /** 一次最多处理多少单，避免单次任务跑太久 */
    private static final int BATCH_SIZE = 200;

    @Resource
    private IProductOrderService productOrderService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    /** 启动 30s 后开始，之后每 60s 跑一次（fixedDelay：上一次跑完再计时，不会堆积） */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void scheduledCloseExpiredOrders() {
        int closed = closeExpiredOnce();
        if (closed > 0) {
            log.info("[order] 定时任务关闭超时订单 {} 单", closed);
        }
    }

    /**
     * 扫描并关闭超时订单，返回成功关闭的单数。
     * public 方法：既给定时任务用，也给「手动触发」的演示接口用。
     */
    public int closeExpiredOnce() {
        return closeExpiredProductOrders() + closeExpiredVoucherOrders();
    }

    private int closeExpiredProductOrders() {
        List<ProductOrder> expired = productOrderService.listExpired(BATCH_SIZE);
        if (expired == null || expired.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (ProductOrder order : expired) {
            try {
                if (productOrderService.closeOrder(order.getId())) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("[order] 关闭商品订单失败 orderId={}", order.getId(), e);
            }
        }
        return closed;
    }

    private int closeExpiredVoucherOrders() {
        List<VoucherOrder> expired = voucherOrderService.listExpired(BATCH_SIZE);
        if (expired == null || expired.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (VoucherOrder order : expired) {
            try {
                if (voucherOrderService.closeOrder(order.getId())) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("[order] 关闭券订单失败 orderId={}", order.getId(), e);
            }
        }
        return closed;
    }
}
