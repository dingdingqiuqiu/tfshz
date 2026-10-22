package com.tfsh.Service.impl;

import com.tfsh.Service.IShopService;
import com.tfsh.Service.IVoucherService;
import com.tfsh.dto.Result;
import com.tfsh.dto.UserDTO;
import com.tfsh.dto.VoucherOrderVO;
import com.tfsh.entity.Shop;
import com.tfsh.entity.Voucher;
import com.tfsh.entity.VoucherOrder;
import com.tfsh.mapper.VoucherOrderMapper;
import com.tfsh.order.StockService;
import com.tfsh.pay.PayResult;
import com.tfsh.pay.PayService;
import com.tfsh.seckill.SeckillOrderPersistService;
import com.tfsh.seckill.SeckillStockRollback;
import com.tfsh.seckill.SeckillStockWarmer;
import com.tfsh.support.MybatisPlusTestSupport;
import com.tfsh.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 秒杀券订单：核销（防重复）与超时关单（回补库存）的 CAS 边界。
 *
 * 券单与商品单"统一"后最容易出问题的地方：
 *   - 核销必须一次成功、二次拒绝；
 *   - 关单必须先 CAS 成功，再同时回补 DB 库存与 Redis 预扣（并把用户从已下单集合移除）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherOrderServiceImplTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    @Mock
    private SeckillStockWarmer stockWarmer;

    @Mock
    private SeckillOrderPersistService persistService;

    @Mock
    private SeckillStockRollback stockRollback;

    @Mock
    private StockService stockService;

    @Mock
    private IVoucherService voucherService;

    @Mock
    private IShopService shopService;

    @Mock
    private PayService payService;

    @InjectMocks
    private VoucherOrderServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestSupport.init(VoucherOrder.class);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @BeforeEach
    void setUp() {
        // 券单的支付/取消/查询都要从 UserHolder 取登录用户
        UserDTO loginUser = new UserDTO();
        loginUser.setId(7L);
        UserHolder.setUser(loginUser);
        ReflectionTestUtils.setField(service, "baseMapper", voucherOrderMapper);

        Voucher voucher = new Voucher();
        voucher.setId(2L);
        voucher.setShopId(1L);
        voucher.setTitle("测试秒杀券");
        voucher.setPayValue(1000L);
        voucher.setActualValue(5000L);
        when(voucherService.getById(2L)).thenReturn(voucher);

        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("103茶餐厅");
        when(shopService.getById(1L)).thenReturn(shop);

        when(payService.prepay(any())).thenReturn(new PayResult());
        when(payService.queryPaid(any())).thenReturn(true);
        when(stockService.restoreSeckillVoucher(2L)).thenReturn(true);
    }

    private VoucherOrder order(int status) {
        VoucherOrder order = new VoucherOrder();
        order.setId(555L);
        order.setUserId(7L);
        order.setVoucherId(2L);
        order.setStatus(status);
        order.setVersion(0);
        order.setVerifyCode("VABCDEFGHJ");
        return order;
    }

    // ---------------- 核销 ----------------

    @Test
    @DisplayName("核销码不存在")
    void verifyUnknownCode() {
        // getOne(wrapper,false) 在不同 MyBatis-Plus 版本里可能走 selectList 也可能走 selectOne，两个都打桩
        when(voucherOrderMapper.selectList(any())).thenReturn(List.of());
        when(voucherOrderMapper.selectOne(any())).thenReturn(null);

        Result result = service.verifyByCode("VNOTEXIST0");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("核销码不存在");
    }

    @Test
    @DisplayName("未支付订单不能核销")
    void verifyUnpaidOrder() {
        VoucherOrder unpaid = order(1);
        when(voucherOrderMapper.selectList(any())).thenReturn(List.of(unpaid));
        when(voucherOrderMapper.selectOne(any())).thenReturn(unpaid);

        Result result = service.verifyByCode("VABCDEFGHJ");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("未支付");
    }

    @Test
    @DisplayName("已核销订单不能重复核销")
    void verifyAlreadyVerified() {
        VoucherOrder verified = order(3);
        when(voucherOrderMapper.selectList(any())).thenReturn(List.of(verified));
        when(voucherOrderMapper.selectOne(any())).thenReturn(verified);

        Result result = service.verifyByCode("VABCDEFGHJ");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("已核销");
    }

    @Test
    @DisplayName("核销成功：CAS（status=2 and version=0）命中后变为已核销")
    void verifySuccess() {
        VoucherOrder paid = order(2);
        VoucherOrder verified = order(3);
        when(voucherOrderMapper.selectList(any())).thenReturn(List.of(paid));
        when(voucherOrderMapper.selectOne(any())).thenReturn(paid);
        when(voucherOrderMapper.selectById(555L)).thenReturn(paid, verified);
        when(voucherOrderMapper.update(any(), any())).thenReturn(1);

        Result result = service.verifyByCode("vabcdefghj");

        assertThat(result.getSuccess()).isTrue();
        assertThat(((VoucherOrderVO) result.getData()).getStatus()).isEqualTo(3);
    }

    // ---------------- 超时关单 ----------------

    @Test
    @DisplayName("关单成功：CAS 命中后同时回补 DB 库存与 Redis 预扣")
    void closeOrderRestoresBothStocks() {
        VoucherOrder waiting = order(1);
        when(voucherOrderMapper.selectById(555L)).thenReturn(waiting, waiting);
        when(voucherOrderMapper.update(any(), any())).thenReturn(1);

        assertThat(service.closeOrder(555L)).isTrue();
        verify(stockService).restoreSeckillVoucher(2L);
        verify(stockRollback).rollback(2L, 7L);
    }

    @Test
    @DisplayName("关单失败（刚被支付）：既不回补 DB 库存，也不回补 Redis 预扣")
    void closeOrderSkipsRestoreWhenCasFails() {
        when(voucherOrderMapper.selectById(555L)).thenReturn(order(1));
        when(voucherOrderMapper.update(any(), any())).thenReturn(0);

        assertThat(service.closeOrder(555L)).isFalse();
        verifyNoInteractions(stockService);
        verify(stockRollback, never()).rollback(any(), any());
    }

    // ---------------- 支付 ----------------

    @Test
    @DisplayName("已关闭的券订单不能支付")
    void payClosedOrder() {
        when(voucherOrderMapper.selectById(555L)).thenReturn(order(4));

        Result result = service.payOrder(555L);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("已关闭");
        verify(payService, never()).prepay(any());
    }

    @Test
    @DisplayName("已支付的券订单重复支付：幂等返回成功，不再发起预下单")
    void payIsIdempotent() {
        when(voucherOrderMapper.selectById(555L)).thenReturn(order(2));

        Result result = service.payOrder(555L);

        assertThat(result.getSuccess()).isTrue();
        verify(payService, never()).prepay(any());
    }
}
