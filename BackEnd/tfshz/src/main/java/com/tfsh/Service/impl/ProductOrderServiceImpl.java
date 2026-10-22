package com.tfsh.Service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IProductOrderService;
import com.tfsh.Service.IProductService;
import com.tfsh.Service.IShopService;
import com.tfsh.dto.ProductOrderForm;
import com.tfsh.dto.ProductOrderVO;
import com.tfsh.dto.Result;
import com.tfsh.dto.UserDTO;
import com.tfsh.entity.Product;
import com.tfsh.entity.ProductOrder;
import com.tfsh.entity.ProductOrderItem;
import com.tfsh.entity.Shop;
import com.tfsh.mapper.ProductOrderItemMapper;
import com.tfsh.mapper.ProductOrderMapper;
import com.tfsh.order.BizException;
import com.tfsh.order.OrderStatus;
import com.tfsh.order.StockService;
import com.tfsh.order.VerifyCodeGenerator;
import com.tfsh.pay.PayRequest;
import com.tfsh.pay.PayResult;
import com.tfsh.pay.PayService;
import com.tfsh.utils.RedisIdWorker;
import com.tfsh.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 商品下单 / 支付 / 核销 / 超时关单。
 *
 * 并发要点（本类存在的原因）：
 *   1. 扣库存用条件更新 `stock = stock - n where stock >= n`，不会超卖；
 *   2. 订单状态流转全部走 **版本号 CAS**：`where id=? and status=期望值 and version=读到的版本`，
 *      所以「支付」和「超时关单」同时发生时，只有一个能成功，不会出现“既已支付又已关闭”；
 *   3. CAS 失败会重读订单再判断（重试 3 次），把冲突转成准确的状态反馈，而不是抛错。
 */
@Slf4j
@Service
public class ProductOrderServiceImpl extends ServiceImpl<ProductOrderMapper, ProductOrder> implements IProductOrderService {

    /** 下单后多少分钟未支付自动关闭（与秒杀券订单共用同一套超时策略） */
    private static final int PAY_TIMEOUT_MINUTES = OrderStatus.PAY_TIMEOUT_MINUTES;
    /** CAS 冲突时的最大重试次数 */
    private static final int CAS_MAX_RETRY = 3;

    @Resource
    private IProductService productService;
    @Resource
    private ProductOrderItemMapper itemMapper;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private PayService payService;
    @Resource
    private IShopService shopService;
    @Resource
    private StockService stockService;

    // ============================================================
    // 下单
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createOrder(ProductOrderForm form) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (form == null || form.getShopId() == null || form.getItems() == null || form.getItems().isEmpty()) {
            return Result.fail("请选择要购买的商品");
        }

        // 同一个商品被点多次时合并数量
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (ProductOrderForm.Item item : form.getItems()) {
            if (item == null || item.getProductId() == null) {
                continue;
            }
            int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            quantities.merge(item.getProductId(), quantity, Integer::sum);
        }
        if (quantities.isEmpty()) {
            return Result.fail("请选择要购买的商品");
        }

        Map<Long, Product> productMap = productService.listByIds(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product, (a, b) -> a));

        long total = 0L;
        List<ProductOrderItem> items = new ArrayList<>(quantities.size());
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (product == null) {
                return Result.fail("商品不存在或已下架");
            }
            if (!Objects.equals(product.getShopId(), form.getShopId())) {
                return Result.fail("商品「" + product.getName() + "」不属于该店铺");
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                return Result.fail("商品「" + product.getName() + "」已下架");
            }

            int quantity = entry.getValue();
            // 条件更新：库存够才扣，原子且不会超卖（见 StockService）
            boolean deducted = stockService.deductProduct(product.getId(), quantity);
            if (!deducted) {
                // 抛业务异常 -> 事务回滚，前面已经扣掉的库存会一起退回
                throw new BizException("商品「" + product.getName() + "」库存不足，请调整数量");
            }

            total += (long) product.getPrice() * quantity;
            items.add(new ProductOrderItem()
                    .setProductId(product.getId())
                    .setProductName(product.getName())
                    .setPrice(product.getPrice())
                    .setQuantity(quantity));
        }

        long orderId = redisIdWorker.nextId("porder");
        LocalDateTime now = LocalDateTime.now();
        ProductOrder order = new ProductOrder()
                .setId(orderId)
                .setUserId(user.getId())
                .setShopId(form.getShopId())
                .setTotalAmount((int) total)
                .setStatus(OrderStatus.WAIT_PAY)
                .setVerifyCode(nextVerifyCode())
                .setVersion(0)
                .setExpireTime(now.plusMinutes(PAY_TIMEOUT_MINUTES))
                .setCreateTime(now)
                .setUpdateTime(now);
        save(order);

        for (ProductOrderItem item : items) {
            item.setOrderId(orderId);
            itemMapper.insert(item);
        }

        log.info("[product-order] 创建订单 orderId={} userId={} shopId={} total={}分 库存已扣",
                orderId, user.getId(), form.getShopId(), total);
        return Result.ok(buildVO(order));
    }

    // ============================================================
    // 支付（演示通道，预留真实支付）
    // ============================================================
    @Override
    public Result payOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        ProductOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), user.getId())) {
            return Result.fail("无权操作该订单");
        }
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.VERIFIED) {
            return Result.ok(buildVO(order));       // 幂等：重复点击支付直接返回成功
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            return Result.fail("订单已关闭，请重新下单");
        }

        // ① 预留的支付通道：真实实现这里会向第三方「统一下单」，把支付参数给前端
        PayResult prepay = payService.prepay(PayRequest.of(order.getId(), order.getTotalAmount(), "商品订单"));
        // ② 查单确认（真实实现要验签 + 查单；演示环境直接为 true）
        if (!payService.queryPaid(prepay.getTradeNo())) {
            return Result.fail("支付未完成，请稍后重试");
        }
        // ③ 与回调走同一条 CAS 路径落状态
        return confirmPaidByNotify(orderId, prepay.getTradeNo());
    }

    @Override
    public Result confirmPaidByNotify(Long orderId, String tradeNo) {
        ProductOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.VERIFIED) {
            return Result.ok(buildVO(order));
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            return Result.fail("订单已关闭，无法支付");
        }

        boolean paid = casStatus(orderId, OrderStatus.WAIT_PAY, OrderStatus.PAID,
                wrapper -> wrapper.set(ProductOrder::getPayTime, LocalDateTime.now()));
        if (!paid) {
            // CAS 失败：大概率是用户在支付的同时被定时任务关单了，重新读取给出准确结论
            ProductOrder latest = getById(orderId);
            if (latest == null) {
                return Result.fail("订单不存在");
            }
            if (latest.getStatus() == OrderStatus.CLOSED) {
                log.warn("[product-order] 支付与关单竞争，关单获胜 orderId={} tradeNo={}", orderId, tradeNo);
                return Result.fail("订单已超时关闭，支付未生效");
            }
            if (latest.getStatus() == OrderStatus.PAID || latest.getStatus() == OrderStatus.VERIFIED) {
                return Result.ok(buildVO(latest));
            }
            return Result.fail("支付失败，请重试");
        }
        log.info("[product-order] 支付成功 orderId={} tradeNo={} status=PAID", orderId, tradeNo);
        return Result.ok(buildVO(getById(orderId)));
    }

    // ============================================================
    // 取消 / 超时关单（都要回补库存）
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancelOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        ProductOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), user.getId())) {
            return Result.fail("无权操作该订单");
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            return Result.ok(buildVO(order));        // 幂等
        }
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.VERIFIED) {
            return Result.fail("订单已支付，无法取消");
        }

        boolean closed = casStatus(orderId, OrderStatus.WAIT_PAY, OrderStatus.CLOSED,
                wrapper -> wrapper.set(ProductOrder::getCloseTime, LocalDateTime.now()));
        if (!closed) {
            ProductOrder latest = getById(orderId);
            if (latest != null && latest.getStatus() == OrderStatus.PAID) {
                return Result.fail("订单已支付，无法取消");
            }
            return Result.fail("取消失败，请刷新后重试");
        }
        restoreStock(orderId);
        log.info("[product-order] 用户取消订单，库存已回补 orderId={}", orderId);
        return Result.ok(buildVO(getById(orderId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(Long orderId) {
        boolean closed = casStatus(orderId, OrderStatus.WAIT_PAY, OrderStatus.CLOSED,
                wrapper -> wrapper.set(ProductOrder::getCloseTime, LocalDateTime.now()));
        if (!closed) {
            // 说明这一单已经被支付了（或已被别的实例关掉），跳过即可，绝不能回补库存
            log.info("[product-order] 关单跳过：状态已变化 orderId={}", orderId);
            return false;
        }
        restoreStock(orderId);
        log.info("[product-order] 超时关单成功，库存已回补 orderId={}", orderId);
        return true;
    }

    @Override
    public List<ProductOrder> listExpired(int limit) {
        return list(Wrappers.<ProductOrder>lambdaQuery()
                .eq(ProductOrder::getStatus, OrderStatus.WAIT_PAY)
                .lt(ProductOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(ProductOrder::getExpireTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    // ============================================================
    // 核销
    // ============================================================
    @Override
    public Result verify(String code) {
        if (StrUtil.isBlank(code)) {
            return Result.fail("请输入核销码");
        }
        ProductOrder order = getOne(Wrappers.<ProductOrder>lambdaQuery()
                .eq(ProductOrder::getVerifyCode, code.trim().toUpperCase()), false);
        if (order == null) {
            return Result.fail("核销码不存在");
        }
        if (order.getStatus() == OrderStatus.WAIT_PAY) {
            return Result.fail("订单未支付，无法核销");
        }
        if (order.getStatus() == OrderStatus.VERIFIED) {
            return Result.fail("该订单已核销，请勿重复核销");
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            return Result.fail("订单已关闭，无法核销");
        }

        // 同一张码被两台终端同时扫：CAS 保证只有一次成功
        boolean verified = casStatus(order.getId(), OrderStatus.PAID, OrderStatus.VERIFIED,
                wrapper -> wrapper.set(ProductOrder::getVerifyTime, LocalDateTime.now()));
        if (!verified) {
            return Result.fail("核销失败：订单状态已变化，请重新扫码");
        }
        log.info("[product-order] 核销成功 orderId={} code={}", order.getId(), order.getVerifyCode());
        return Result.ok(buildVO(getById(order.getId())));
    }

    // ============================================================
    // 查询
    // ============================================================
    @Override
    public Result queryOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        ProductOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), user.getId())) {
            return Result.fail("无权查看该订单");
        }
        return Result.ok(buildVO(order));
    }

    @Override
    public Result queryMyOrders() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        List<ProductOrder> orders = list(Wrappers.<ProductOrder>lambdaQuery()
                .eq(ProductOrder::getUserId, user.getId())
                .orderByDesc(ProductOrder::getCreateTime)
                .last("LIMIT 30"));
        return Result.ok(orders.stream().map(this::buildVO).collect(Collectors.toList()));
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 版本号 CAS 状态流转（带重试）。
     *
     * SQL 形态：UPDATE tb_product_order
     *            SET status = ?, version = version + 1, ...
     *          WHERE id = ? AND status = ? AND version = ?
     *
     * 返回 true 表示「本次调用」完成了状态变更；false 表示订单已被别的路径改过（支付/关单/核销竞争）。
     */
    private boolean casStatus(Long orderId, int expectStatus, int targetStatus,
                              Consumer<LambdaUpdateWrapper<ProductOrder>> extraSets) {
        for (int attempt = 1; attempt <= CAS_MAX_RETRY; attempt++) {
            ProductOrder current = getById(orderId);
            if (current == null || current.getStatus() == null || current.getStatus() != expectStatus) {
                return false;
            }
            LambdaUpdateWrapper<ProductOrder> wrapper = Wrappers.<ProductOrder>lambdaUpdate()
                    .eq(ProductOrder::getId, orderId)
                    .eq(ProductOrder::getStatus, expectStatus)
                    .eq(ProductOrder::getVersion, current.getVersion())
                    .set(ProductOrder::getStatus, targetStatus)
                    .setSql("version = version + 1");
            if (extraSets != null) {
                extraSets.accept(wrapper);
            }
            if (update(wrapper)) {
                return true;
            }
            log.warn("[product-order] CAS 冲突 orderId={} 第 {}/{} 次重试（status {} -> {}）",
                    orderId, attempt, CAS_MAX_RETRY, expectStatus, targetStatus);
        }
        return false;
    }

    /** 回补库存：只在 CAS 成功（订单真的被关闭）之后调用，避免“关单失败还退库存” */
    private void restoreStock(Long orderId) {
        List<ProductOrderItem> items = itemMapper.selectList(Wrappers.<ProductOrderItem>lambdaQuery()
                .eq(ProductOrderItem::getOrderId, orderId));
        for (ProductOrderItem item : items) {
            stockService.restoreProduct(item.getProductId(), item.getQuantity());
        }
    }

    /** 生成不重复的核销码：前缀 P + 9 位随机，唯一索引兜底 */
    private String nextVerifyCode() {
        for (int i = 0; i < 5; i++) {
            String code = VerifyCodeGenerator.next(VerifyCodeGenerator.PREFIX_PRODUCT);
            Long exists = query().eq("verify_code", code).count();
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BizException("生成核销码失败，请重试");
    }

    private ProductOrderVO buildVO(ProductOrder order) {
        ProductOrderVO vo = new ProductOrderVO();
        vo.setOrderId(String.valueOf(order.getId()));
        vo.setShopId(order.getShopId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.text(order.getStatus()));
        vo.setVerifyCode(order.getVerifyCode());
        vo.setExpireTime(order.getExpireTime());
        vo.setPayTime(order.getPayTime());
        vo.setVerifyTime(order.getVerifyTime());
        vo.setCreateTime(order.getCreateTime());

        Shop shop = shopService.getById(order.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getName());
        }

        List<ProductOrderItem> items = itemMapper.selectList(Wrappers.<ProductOrderItem>lambdaQuery()
                .eq(ProductOrderItem::getOrderId, order.getId())
                .orderByAsc(ProductOrderItem::getId));
        vo.setItems(items.stream()
                .map(item -> new ProductOrderVO.ItemVO(item.getProductName(), item.getPrice(), item.getQuantity()))
                .collect(Collectors.toList()));
        return vo;
    }
}
