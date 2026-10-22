package com.tfsh.Service.impl;

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
import com.tfsh.seckill.PersistOutcome;
import com.tfsh.seckill.SeckillOrderPersistService;
import com.tfsh.seckill.SeckillStockWarmer;
import com.tfsh.utils.RedisConstants;
import com.tfsh.utils.RedisIdWorker;
import com.tfsh.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 秒杀下单入口。
 *
 * 主链路（同步部分只做内存/Redis 级操作，绝不碰数据库）：
 *   1. 登录校验（LoginInterceptor + UserHolder）
 *   2. 库存预热（key 不在则从 DB 懒加载）
 *   3. 一段 Lua 原子完成：时间窗校验 -> 一人一单校验 -> 库存预扣 -> 订单消息 XADD 入队
 *   4. 立即返回 orderId；真正的落库交给 SeckillOrderStreamConsumer 异步完成
 *
 * 兜底：Lua 执行异常（脚本报错、Redis 抖动）时降级为“同步下单”，
 * 走 SeckillOrderPersistService 的条件更新 + Redisson 锁，能力等价、只是慢。
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private SeckillStockWarmer stockWarmer;
    @Resource
    private SeckillOrderPersistService persistService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IShopService shopService;

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
            return Result.fail("秒杀券不存在");
        }

        long orderId = redisIdWorker.nextId("order");
        Long code;
        try {
            code = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId,
                            RedisConstants.SECKILL_ORDER_KEY + voucherId,
                            RedisConstants.SECKILL_META_KEY + voucherId,
                            RedisConstants.SECKILL_ORDER_STREAM),
                    user.getId().toString(),
                    voucherId.toString(),
                    String.valueOf(orderId),
                    String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            // 脚本/Redis 异常不能让用户白等：降级同步下单，仍然受条件更新 + Redisson 锁保护
            log.error("[seckill] Lua 执行异常，降级为同步下单 voucherId={} userId={}", voucherId, user.getId(), e);
            return createOrderNow(orderId, user.getId(), voucherId);
        }

        int result = code == null ? -2 : code.intValue();
        return switch (result) {
            // 抢到资格：库存已预扣、消息已入队，订单由消费者异步落库
            case 0 -> Result.ok(orderId);
            case 1 -> Result.fail("库存不足");
            case 2 -> Result.fail("不能重复下单");
            case 3 -> Result.fail("秒杀尚未开始");
            case 4 -> Result.fail("秒杀已经结束");
            default -> Result.fail("秒杀活动未就绪");
        };
    }

    /** 同步兜底：不经过消息队列，直接落库（异常场景下的可用性保底） */
    private Result createOrderNow(long orderId, Long userId, Long voucherId) {
        VoucherOrder order = buildOrder(orderId, userId, voucherId);
        try {
            PersistOutcome outcome = persistService.persist(order);
            return switch (outcome) {
                case SUCCESS, ALREADY_EXISTS -> Result.ok(orderId);
                case USER_DUPLICATE -> Result.fail("不能重复下单");
                case STOCK_EXHAUSTED -> Result.fail("库存不足");
                default -> Result.fail("下单失败，请稍后重试");
            };
        } catch (Exception e) {
            log.error("[seckill] 同步兜底下单失败 orderId={}", orderId, e);
            return Result.fail("下单失败，请稍后重试");
        }
    }

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
        return Result.ok(order);
    }

    /**
     * 我的优惠券：订单 + 券 + 店铺三张表拼成前端列表需要的字段。
     * 订单是异步落库的，所以用户刚抢完立刻查可能少一条，稍后刷新即可。
     */
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
            VoucherOrderVO vo = new VoucherOrderVO();
            // 订单号是 19 位数字，用字符串返回，避免前端 Number 丢精度
            vo.setOrderId(String.valueOf(order.getId()));
            vo.setVoucherId(order.getVoucherId());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            Voucher voucher = voucherMap.get(order.getVoucherId());
            if (voucher != null) {
                vo.setTitle(voucher.getTitle());
                vo.setSubTitle(voucher.getSubTitle());
                vo.setPayValue(voucher.getPayValue());
                vo.setActualValue(voucher.getActualValue());
                vo.setShopId(voucher.getShopId());
                vo.setShopName(shopNameMap.get(voucher.getShopId()));
            }
            result.add(vo);
        }
        return Result.ok(result);
    }

    private VoucherOrder buildOrder(long orderId, Long userId, Long voucherId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        order.setCreateTime(now);
        order.setUpdateTime(now);
        return order;
    }
}
