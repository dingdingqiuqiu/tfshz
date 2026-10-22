package com.tfsh.seckill;

import com.tfsh.entity.VoucherOrder;
import com.tfsh.mapper.VoucherOrderMapper;
import com.tfsh.order.StockService;
import com.tfsh.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀落库的幂等与并发兜底 —— 防超卖的最后一道闸门，覆盖四个分支：
 *   ① orderId 已存在（重复投递）  → 幂等成功，什么都不做
 *   ② 用户已有订单（一人一单）      → USER_DUPLICATE
 *   ③ DB 条件扣减失败（库存不足）   → STOCK_EXHAUSTED
 *   ④ 抢锁失败（并发争用）          → 可重试异常，消息不能当成功 ACK
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeckillOrderPersistServiceTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    @Mock
    private StockService stockService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private SeckillOrderPersistService persistService;

    private VoucherOrder order;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestSupport.init(VoucherOrder.class);
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        order = new VoucherOrder();
        order.setId(1001L);
        order.setUserId(7L);
        order.setVoucherId(2L);

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(any(Long.class), any(Long.class), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherOrderMapper.insert(any(VoucherOrder.class))).thenReturn(1);
        when(voucherOrderMapper.selectById(1001L)).thenReturn(null);
        when(voucherOrderMapper.selectCount(any())).thenReturn(0L);
        when(stockService.deductSeckillVoucher(2L)).thenReturn(true);
    }

    @Test
    @DisplayName("orderId 已落库：直接返回 ALREADY_EXISTS，不扣库存不插入（重复投递幂等）")
    void alreadyExistsIsIdempotent() {
        when(voucherOrderMapper.selectById(1001L)).thenReturn(order);

        assertThat(persistService.persist(order)).isEqualTo(PersistOutcome.ALREADY_EXISTS);
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
        verify(stockService, never()).deductSeckillVoucher(any());
    }

    @Test
    @DisplayName("用户已有该券订单：返回 USER_DUPLICATE，不重复扣库存")
    void userDuplicateIsRejected() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(1L);

        assertThat(persistService.persist(order)).isEqualTo(PersistOutcome.USER_DUPLICATE);
        verify(stockService, never()).deductSeckillVoucher(any());
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
    }

    @Test
    @DisplayName("DB 条件扣减失败（stock 已经为 0）：返回 STOCK_EXHAUSTED，不插入订单")
    void stockExhausted() {
        when(stockService.deductSeckillVoucher(2L)).thenReturn(false);

        assertThat(persistService.persist(order)).isEqualTo(PersistOutcome.STOCK_EXHAUSTED);
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
    }

    @Test
    @DisplayName("正常路径：扣减成功才插入订单")
    void successPath() {
        assertThat(persistService.persist(order)).isEqualTo(PersistOutcome.SUCCESS);
        verify(stockService).deductSeckillVoucher(2L);
        verify(voucherOrderMapper).insert(order);
    }

    @Test
    @DisplayName("抢锁超时：抛可重试异常（消息留在队列重试，不能当成功 ACK）")
    void lockTimeoutIsRetryable() throws InterruptedException {
        when(lock.tryLock(any(Long.class), any(Long.class), any(TimeUnit.class))).thenReturn(false);

        assertThatThrownBy(() -> persistService.persist(order))
                .isInstanceOf(SeckillRetryLaterException.class)
                .hasMessageContaining("获取下单锁超时");
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
    }

    @Test
    @DisplayName("字段缺失：直接判定为 INVALID，不做任何副作用")
    void invalidMessage() {
        VoucherOrder broken = new VoucherOrder();
        broken.setId(1002L);

        assertThat(persistService.persist(broken)).isEqualTo(PersistOutcome.INVALID);
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
    }
}
