package com.tfsh.Service.impl;

import com.tfsh.Service.IProductService;
import com.tfsh.Service.IShopService;
import com.tfsh.dto.ProductOrderForm;
import com.tfsh.dto.ProductOrderVO;
import com.tfsh.dto.Result;
import com.tfsh.entity.Product;
import com.tfsh.entity.ProductOrder;
import com.tfsh.entity.ProductOrderItem;
import com.tfsh.mapper.ProductOrderItemMapper;
import com.tfsh.mapper.ProductOrderMapper;
import com.tfsh.order.BizException;
import com.tfsh.order.StockService;
import com.tfsh.pay.PayResult;
import com.tfsh.pay.PayService;
import com.tfsh.support.MybatisPlusTestSupport;
import com.tfsh.utils.RedisIdWorker;
import com.tfsh.utils.UserHolder;
import com.tfsh.dto.UserDTO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品订单：库存扣减边界 + 版本号 CAS 状态流转。
 *
 * 重点验证三条：
 *   I1 库存守恒：条件扣减失败（库存不足）时不能生成订单；
 *   I2 一次迁移：CAS 成功才改状态，失败时按最新状态给出准确结论；
 *   以及最容易被写错的一条 —— **关单 CAS 失败绝不能回补库存**（否则"已支付却退了库存"）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductOrderServiceImplTest {

    @Mock
    private ProductOrderMapper productOrderMapper;

    @Mock
    private IProductService productService;

    @Mock
    private StockService stockService;

    @Mock
    private ProductOrderItemMapper itemMapper;

    @Mock
    private RedisIdWorker redisIdWorker;

    @Mock
    private PayService payService;

    @Mock
    private IShopService shopService;

    @InjectMocks
    private ProductOrderServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestSupport.init(ProductOrder.class, ProductOrderItem.class);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @BeforeEach
    void setUp() {
        // 业务入口都要从 UserHolder 取登录用户，单测里手动塞一个
        UserDTO loginUser = new UserDTO();
        loginUser.setId(1L);
        UserHolder.setUser(loginUser);
        ReflectionTestUtils.setField(service, "baseMapper", productOrderMapper);
        when(redisIdWorker.nextId(anyString())).thenReturn(9001L);
        when(productOrderMapper.insert(any(ProductOrder.class))).thenReturn(1);
        when(productOrderMapper.selectById(1L)).thenReturn(null);
        when(itemMapper.insert(any(ProductOrderItem.class))).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(payService.prepay(any())).thenReturn(new PayResult());
        when(payService.queryPaid(any())).thenReturn(true);
        when(shopService.getById(any())).thenReturn(null);
    }

    private Product product(int stock) {
        Product product = new Product();
        product.setId(1L);
        product.setShopId(1L);
        product.setName("黯然销魂饭");
        product.setPrice(3800);
        product.setStock(stock);
        product.setStatus(1);
        return product;
    }

    private ProductOrderForm form(int quantity) {
        ProductOrderForm.Item item = new ProductOrderForm.Item();
        item.setProductId(1L);
        item.setQuantity(quantity);
        ProductOrderForm form = new ProductOrderForm();
        form.setShopId(1L);
        form.setItems(List.of(item));
        return form;
    }

    private ProductOrder order(int status) {
        ProductOrder order = new ProductOrder();
        order.setId(1L);
        order.setUserId(1L);
        order.setShopId(1L);
        order.setTotalAmount(3800);
        order.setStatus(status);
        order.setVersion(0);
        order.setVerifyCode("PABCDEFGHJ");
        return order;
    }

    // ---------------- 下单：库存边界 ----------------

    @Test
    @DisplayName("下单成功：条件扣减通过后生成待支付订单，金额与明细正确")
    void createOrderSuccess() {
        when(productService.listByIds(anyCollection())).thenReturn(List.of(product(10)));
        when(stockService.deductProduct(1L, 2)).thenReturn(true);

        Result result = service.createOrder(form(2));

        assertThat(result.getSuccess()).isTrue();
        ProductOrderVO vo = (ProductOrderVO) result.getData();
        assertThat(vo.getTotalAmount()).isEqualTo(7600);
        assertThat(vo.getStatus()).isEqualTo(1);
        verify(productOrderMapper).insert(any(ProductOrder.class));
        verify(itemMapper).insert(any(ProductOrderItem.class));
    }

    @Test
    @DisplayName("库存不足：条件扣减失败 -> 抛业务异常，不落订单（库存不会被扣成负数）")
    void createOrderStockNotEnough() {
        when(productService.listByIds(anyCollection())).thenReturn(List.of(product(1)));
        when(stockService.deductProduct(1L, 5)).thenReturn(false);

        assertThatThrownBy(() -> service.createOrder(form(5)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
        verify(productOrderMapper, never()).insert(any(ProductOrder.class));
    }

    @Test
    @DisplayName("空购物车：直接拒绝，不触碰库存与数据库")
    void createOrderEmptyItems() {
        ProductOrderForm empty = new ProductOrderForm();
        empty.setShopId(1L);

        Result result = service.createOrder(empty);

        assertThat(result.getSuccess()).isFalse();
        verifyNoInteractions(stockService);
    }

    // ---------------- 支付：CAS 边界 ----------------

    @Test
    @DisplayName("支付成功：CAS（status=1 and version=0）命中后订单变为待核销")
    void paySuccess() {
        ProductOrder waiting = order(1);
        ProductOrder paid = order(2);
        // 依次是：支付前读取 → 支付确认读取 → CAS 读取 → 成功后读取详情
        when(productOrderMapper.selectById(1L)).thenReturn(waiting, waiting, waiting, paid);
        when(productOrderMapper.update(any(), any())).thenReturn(1);

        Result result = service.payOrder(1L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(((ProductOrderVO) result.getData()).getStatus()).isEqualTo(2);
        verify(productOrderMapper).update(any(), any());
    }

    @Test
    @DisplayName("支付被关单抢先：CAS 连续失败且订单已关闭 -> 明确返回“已超时关闭”，不能报成功")
    void payLosesRaceWithClose() {
        ProductOrder waiting = order(1);
        ProductOrder closed = order(4);
        // 支付前读取 → 确认读取 → 3 次 CAS 读取（都失败） → 最终读取到已关闭
        when(productOrderMapper.selectById(1L))
                .thenReturn(waiting, waiting, waiting, waiting, waiting, closed);
        when(productOrderMapper.update(any(), any())).thenReturn(0);

        Result result = service.payOrder(1L);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("已超时关闭");
    }

    @Test
    @DisplayName("已关闭订单不能再支付：直接拒绝，不发起预下单")
    void payClosedOrderRejected() {
        when(productOrderMapper.selectById(1L)).thenReturn(order(4));

        Result result = service.payOrder(1L);

        assertThat(result.getSuccess()).isFalse();
        verify(payService, never()).prepay(any());
    }

    // ---------------- 关单：回补库存的门控 ----------------

    @Test
    @DisplayName("关单成功：CAS 命中后才回补库存")
    void closeOrderRestoresStockAfterCas() {
        ProductOrder waiting = order(1);
        when(productOrderMapper.selectById(1L)).thenReturn(waiting, waiting);
        when(productOrderMapper.update(any(), any())).thenReturn(1);
        ProductOrderItem item = new ProductOrderItem();
        item.setOrderId(1L);
        item.setProductId(1L);
        item.setQuantity(2);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        assertThat(service.closeOrder(1L)).isTrue();
        verify(stockService).restoreProduct(1L, 2);
    }

    @Test
    @DisplayName("关单失败（已被支付）：绝不回补库存 —— 否则就是资损")
    void closeOrderDoesNotRestoreStockWhenCasFails() {
        when(productOrderMapper.selectById(1L)).thenReturn(order(1));
        when(productOrderMapper.update(any(), any())).thenReturn(0);

        assertThat(service.closeOrder(1L)).isFalse();
        verifyNoInteractions(stockService);
        verify(itemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("已支付订单不可取消")
    void cancelPaidOrderRejected() {
        when(productOrderMapper.selectById(1L)).thenReturn(order(2));

        Result result = service.cancelOrder(1L);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("已支付");
    }
}
