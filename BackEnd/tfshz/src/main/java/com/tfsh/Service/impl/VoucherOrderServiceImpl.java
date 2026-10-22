package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IShopService;
import com.tfsh.Service.IVoucherOrderService;
import com.tfsh.Service.IVoucherService;
import com.tfsh.dto.Result;
import com.tfsh.dto.UserDTO;
import com.tfsh.dto.VoucherOrderVO;
import com.tfsh.entity.Shop;
import com.tfsh.entity.Voucher;
import com.tfsh.entity.VoucherOrder;
import com.tfsh.mapper.VoucherOrderMapper;
import com.tfsh.order.OrderStatus;
import com.tfsh.order.StockService;
import com.tfsh.pay.PayRequest;
import com.tfsh.pay.PayResult;
import com.tfsh.pay.PayService;
import com.tfsh.seckill.PersistOutcome;
import com.tfsh.seckill.SeckillMetrics;
import com.tfsh.seckill.SeckillOrderPersistService;
import com.tfsh.seckill.SeckillStockRollback;
import com.tfsh.seckill.SeckillStockWarmer;
import com.tfsh.seckill.mq.SeckillOrderMessageCodec;
import com.tfsh.seckill.mq.SeckillOrderPublisher;
import com.tfsh.utils.RedisConstants;
import com.tfsh.utils.RedisIdWorker;
import com.tfsh.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 秒杀券订单。
 *
 * 抢券这一步是秒杀特有的（Redis 预扣 + Lua + Stream 异步落库），
 * 但**抢到之后的订单生命周期与商品订单完全同构**：
 *   支付（PayService 抽象）→ 得核销码 → 扫二维码核销 → 超时未支付自动关单并回补库存，
 *   所有状态流转都走「版本号 CAS」，解决支付/关单并发。
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /** CAS 冲突时的最大重试次数（与商品订单一致） */
    private static final int CAS_MAX_RETRY = 3;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private SeckillStockWarmer stockWarmer;
    @Resource
    private SeckillOrderPersistService persistService;
    @Resource
    private SeckillStockRollback stockRollback;
    @Resource
    private SeckillOrderPublisher seckillOrderPublisher;
    @Resource
    private SeckillOrderMessageCodec codec;
    @Resource
    private SeckillMetrics metrics;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IShopService shopService;
    @Resource
    private StockService stockService;
    @Resource
    private PayService payService;

    // ============================================================
    // 抢券（秒杀特性）
    // ============================================================
    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (voucherId == null) {
            return Result.fail("参数错误");
        }

        // 库存 key 不在就先预热（Redis 被清过 / 新建券），否则 Lua 里 GET 到 nil 会直接报错
        if (!stockWarmer.warmUpIfAbsent(voucherId)) {
            metrics.request(SeckillMetrics.RESULT_NOT_WARMED);
            return Result.fail("秒杀券不存在");
        }

        long orderId = redisIdWorker.nextId("order");
        Long code;
        try {
            code = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId,
                            RedisConstants.SECKILL_ORDER_KEY + voucherId,
                            RedisConstants.SECKILL_META_KEY + voucherId),
                    user.getId().toString(),
                    voucherId.toString(),
                    String.valueOf(orderId),
                    String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            // 脚本/Redis 异常不能让用户白等：降级同步下单，仍然受条件更新 + Redisson 锁保护
            log.error("[seckill] Lua 执行异常，降级为同步下单 voucherId={} userId={}", voucherId, user.getId(), e);
            metrics.request(SeckillMetrics.RESULT_LUA_ERROR);
            return createOrderNow(orderId, user.getId(), voucherId);
        }

        int result = code == null ? -2 : code.intValue();
        if (result == 0) {
            // 抢到资格：库存已预扣 → 投递 MQ 消息 → 消费者异步落库。
            //
            // 注意这里与"Lua 里 XADD"的取舍：
            //   Lua 内入队是原子的（不会出现扣了库存没入队），但 MQ 就被绑死在 Redis 上；
            //   改成 Java 投递后，入队失败必须显式回滚预扣，否则库存被这条没进队的请求吃掉。
            VoucherOrder order = buildOrder(orderId, user.getId(), voucherId);
            if (!seckillOrderPublisher.publish(codec.fromOrder(order))) {
                stockRollback.rollback(voucherId, user.getId());
                log.error("[seckill] 消息投递失败，已回滚预扣 voucherId={} userId={}", voucherId, user.getId());
                metrics.request(SeckillMetrics.RESULT_PUBLISH_FAILED);
                return Result.fail("抢购人数太多，请稍后重试");
            }
            metrics.request(SeckillMetrics.RESULT_SUCCESS);
            // 订单号用字符串返回：19 位数字超过 JS Number 安全范围，用 number 会丢精度
            return Result.ok(String.valueOf(orderId));
        }
        switch (result) {
            case 1 -> {
                metrics.request(SeckillMetrics.RESULT_STOCK_EMPTY);
                return Result.fail("库存不足");
            }
            case 2 -> {
                metrics.request(SeckillMetrics.RESULT_DUPLICATE);
                return Result.fail("不能重复下单");
            }
            case 3 -> {
                metrics.request(SeckillMetrics.RESULT_NOT_STARTED);
                return Result.fail("秒杀尚未开始");
            }
            case 4 -> {
                metrics.request(SeckillMetrics.RESULT_ENDED);
                return Result.fail("秒杀已经结束");
            }
            default -> {
                metrics.request(SeckillMetrics.RESULT_NOT_WARMED);
                return Result.fail("秒杀活动未就绪");
            }
        }
    }

    /** 同步兜底：不经过消息队列，直接落库（异常场景下的可用性保底） */
    private Result createOrderNow(long orderId, Long userId, Long voucherId) {
        VoucherOrder order = buildOrder(orderId, userId, voucherId);
        try {
            PersistOutcome outcome = persistService.persist(order);
            metrics.persist(outcome);
            Result result = switch (outcome) {
                case SUCCESS, ALREADY_EXISTS -> Result.ok(String.valueOf(orderId));
                case USER_DUPLICATE -> Result.fail("不能重复下单");
                case STOCK_EXHAUSTED -> Result.fail("库存不足");
                default -> Result.fail("下单失败，请稍后重试");
            };
            metrics.request(result.getSuccess() ? SeckillMetrics.RESULT_FALLBACK_SYNC : SeckillMetrics.RESULT_STOCK_EMPTY);
            return result;
        } catch (Exception e) {
            log.error("[seckill] 同步兜底下单失败 orderId={}", orderId, e);
            metrics.request(SeckillMetrics.RESULT_FALLBACK_SYNC);
            return Result.fail("下单失败，请稍后重试");
        }
    }

    // ============================================================
    // 支付 / 取消 / 超时关单 / 核销：与商品订单同一套生命周期
    // ============================================================
    @Override
    public Result payOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), user.getId())) {
            return Result.fail("无权操作该订单");
        }
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.VERIFIED) {
            return Result.ok(buildVO(order));       // 幂等
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            return Result.fail("订单已关闭，请重新抢购");
        }

        // 共用支付通道：金额取券的面额，商品/券在支付层没有区别
        Voucher voucher = voucherService.getById(order.getVoucherId());
        Integer amount = voucher == null || voucher.getPayValue() == null ? 0 : voucher.getPayValue().intValue();
        PayResult prepay = payService.prepay(PayRequest.of(order.getId(), amount,
                voucher == null ? "秒杀券" : voucher.getTitle()));
        if (!payService.queryPaid(prepay.getTradeNo())) {
            return Result.fail("支付未完成，请稍后重试");
        }
        return confirmPaid(orderId, prepay.getTradeNo());
    }

    @Override
    public Result confirmPaid(Long orderId, String tradeNo) {
        VoucherOrder order = getById(orderId);
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
                wrapper -> wrapper.set(VoucherOrder::getPayTime, LocalDateTime.now()));
        if (!paid) {
            VoucherOrder latest = getById(orderId);
            if (latest == null) {
                return Result.fail("订单不存在");
            }
            if (latest.getStatus() == OrderStatus.CLOSED) {
                log.warn("[voucher-order] 支付与关单竞争，关单获胜 orderId={} tradeNo={}", orderId, tradeNo);
                return Result.fail("订单已超时关闭，支付未生效");
            }
            if (latest.getStatus() == OrderStatus.PAID || latest.getStatus() == OrderStatus.VERIFIED) {
                return Result.ok(buildVO(latest));
            }
            return Result.fail("支付失败，请重试");
        }
        log.info("[voucher-order] 支付成功 orderId={} tradeNo={} status=PAID", orderId, tradeNo);
        return Result.ok(buildVO(getById(orderId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancelOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(orderId);
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
                wrapper -> wrapper.set(VoucherOrder::getCloseTime, LocalDateTime.now()));
        if (!closed) {
            VoucherOrder latest = getById(orderId);
            if (latest != null && latest.getStatus() == OrderStatus.PAID) {
                return Result.fail("订单已支付，无法取消");
            }
            return Result.fail("取消失败，请刷新后重试");
        }
        restoreStock(order);
        log.info("[voucher-order] 用户取消，库存已回补 orderId={}", orderId);
        return Result.ok(buildVO(getById(orderId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(Long orderId) {
        boolean closed = casStatus(orderId, OrderStatus.WAIT_PAY, OrderStatus.CLOSED,
                wrapper -> wrapper.set(VoucherOrder::getCloseTime, LocalDateTime.now()));
        if (!closed) {
            // 说明刚被支付（或别的实例关了）：绝不能回补库存
            log.info("[voucher-order] 关单跳过：状态已变化 orderId={}", orderId);
            return false;
        }
        VoucherOrder order = getById(orderId);
        if (order != null) {
            restoreStock(order);
        }
        log.info("[voucher-order] 超时关单成功，库存已回补 orderId={}", orderId);
        return true;
    }

    @Override
    public List<VoucherOrder> listExpired(int limit) {
        return list(Wrappers.<VoucherOrder>lambdaQuery()
                .eq(VoucherOrder::getStatus, OrderStatus.WAIT_PAY)
                .isNotNull(VoucherOrder::getExpireTime)
                .lt(VoucherOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(VoucherOrder::getExpireTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public Result verifyByCode(String code) {
        // 用 list + 取第一条代替 getOne：语义完全一样（取不到就是 null），但少一层包装，
        // 单测里只需打桩 selectList 即可，不依赖 MyBatis-Plus 内部对 getOne 的实现差异。
        List<VoucherOrder> matched = list(Wrappers.<VoucherOrder>lambdaQuery()
                .eq(VoucherOrder::getVerifyCode, code.trim().toUpperCase()));
        if (matched == null || matched.isEmpty()) {
            return Result.fail("核销码不存在");
        }
        VoucherOrder order = matched.get(0);
        if (order.getStatus() == OrderStatus.WAIT_PAY) {
            return Result.fail("订单未支付，无法核销");
        }
        if (order.getStatus() == OrderStatus.VERIFIED) {
            return Result.fail("该订单已核销，请勿重复核销");
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            return Result.fail("订单已关闭，无法核销");
        }
        boolean verified = casStatus(order.getId(), OrderStatus.PAID, OrderStatus.VERIFIED,
                wrapper -> wrapper.set(VoucherOrder::getUseTime, LocalDateTime.now()));
        if (!verified) {
            return Result.fail("核销失败：订单状态已变化，请重新扫码");
        }
        log.info("[voucher-order] 核销成功 orderId={} code={}", order.getId(), order.getVerifyCode());
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
        VoucherOrder order = getById(orderId);
        // 异步落库还没轮到这条消息时，查不到是正常现象，返回空让前端稍后再查
        if (order == null) {
            return Result.ok(null);
        }
        if (!user.getId().equals(order.getUserId())) {
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
        List<VoucherOrder> orders = list(Wrappers.<VoucherOrder>lambdaQuery()
                .eq(VoucherOrder::getUserId, user.getId())
                .orderByDesc(VoucherOrder::getCreateTime));
        if (orders == null || orders.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Voucher> voucherMap = voucherService.listByIds(voucherIds).stream()
                .collect(Collectors.toMap(Voucher::getId, v -> v, (a, b) -> a));

        List<Long> shopIds = voucherMap.values().stream()
                .map(Voucher::getShopId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> shopNameMap = shopIds.isEmpty()
                ? Collections.emptyMap()
                : shopService.listByIds(shopIds).stream()
                        .collect(Collectors.toMap(Shop::getId, Shop::getName, (a, b) -> a));

        List<VoucherOrderVO> result = new ArrayList<>(orders.size());
        for (VoucherOrder order : orders) {
            Voucher voucher = voucherMap.get(order.getVoucherId());
            String shopName = voucher == null ? null : shopNameMap.get(voucher.getShopId());
            result.add(buildVO(order, voucher, shopName));
        }
        return Result.ok(result);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 版本号 CAS 状态流转（与商品订单同一套实现）。
     * UPDATE ... SET status=?, version=version+1 WHERE id=? AND status=? AND version=?
     */
    private boolean casStatus(Long orderId, int expectStatus, int targetStatus,
                              Consumer<LambdaUpdateWrapper<VoucherOrder>> extraSets) {
        for (int attempt = 1; attempt <= CAS_MAX_RETRY; attempt++) {
            VoucherOrder current = getById(orderId);
            if (current == null || current.getStatus() == null || current.getStatus() != expectStatus) {
                return false;
            }
            LambdaUpdateWrapper<VoucherOrder> wrapper = Wrappers.<VoucherOrder>lambdaUpdate()
                    .eq(VoucherOrder::getId, orderId)
                    .eq(VoucherOrder::getStatus, expectStatus)
                    .eq(VoucherOrder::getVersion, current.getVersion() == null ? 0 : current.getVersion())
                    .set(VoucherOrder::getStatus, targetStatus)
                    .setSql("version = version + 1");
            if (extraSets != null) {
                extraSets.accept(wrapper);
            }
            if (update(wrapper)) {
                return true;
            }
            log.warn("[voucher-order] CAS 冲突 orderId={} 第 {}/{} 次重试（{} -> {}）",
                    orderId, attempt, CAS_MAX_RETRY, expectStatus, targetStatus);
        }
        return false;
    }

    /**
     * 回补库存：DB 的 stock + 1，Redis 的预扣库存 + 1，并把用户从「已下单集合」移除
     * （移除后该用户还能重新抢，避免"一单过期就永久失去资格"）。
     * 只在 CAS 成功关单之后调用。
     */
    private void restoreStock(VoucherOrder order) {
        stockService.restoreSeckillVoucher(order.getVoucherId());
        stockRollback.rollback(order.getVoucherId(), order.getUserId());
    }

    private VoucherOrder buildOrder(long orderId, Long userId, Long voucherId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(OrderStatus.WAIT_PAY);
        order.setVersion(0);
        LocalDateTime now = LocalDateTime.now();
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setExpireTime(now.plusMinutes(OrderStatus.PAY_TIMEOUT_MINUTES));
        return order;
    }

    private VoucherOrderVO buildVO(VoucherOrder order) {
        Voucher voucher = order.getVoucherId() == null ? null : voucherService.getById(order.getVoucherId());
        String shopName = null;
        if (voucher != null && voucher.getShopId() != null) {
            Shop shop = shopService.getById(voucher.getShopId());
            shopName = shop == null ? null : shop.getName();
        }
        return buildVO(order, voucher, shopName);
    }

    private VoucherOrderVO buildVO(VoucherOrder order, Voucher voucher, String shopName) {
        VoucherOrderVO vo = new VoucherOrderVO();
        // 订单号是 19 位数字，用字符串返回，避免前端 Number 丢精度
        vo.setOrderId(String.valueOf(order.getId()));
        vo.setVoucherId(order.getVoucherId());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.text(order.getStatus()));
        vo.setVerifyCode(order.getVerifyCode());
        vo.setExpireTime(order.getExpireTime());
        vo.setPayTime(order.getPayTime());
        vo.setVerifyTime(order.getUseTime());
        vo.setCreateTime(order.getCreateTime());
        if (voucher != null) {
            vo.setTitle(voucher.getTitle());
            vo.setSubTitle(voucher.getSubTitle());
            vo.setPayValue(voucher.getPayValue());
            vo.setActualValue(voucher.getActualValue());
            vo.setShopId(voucher.getShopId());
            vo.setShopName(shopName);
        }
        return vo;
    }
}
