package com.tfsh.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单状态机文案与常量：状态值是前后端/数据库的契约，改名比改数字安全。
 */
class OrderStatusTest {

    @ParameterizedTest(name = "status={0} -> {1}")
    @CsvSource({
            "1, 待支付",
            "2, 待核销",
            "3, 已核销",
            "4, 已关闭"
    })
    @DisplayName("四个状态各自有稳定文案")
    void textMapping(int status, String expected) {
        assertThat(OrderStatus.text(status)).isEqualTo(expected);
    }

    @Test
    @DisplayName("未知状态与 null 兜底为“未知”，不抛异常")
    void unknownStatus() {
        assertThat(OrderStatus.text(null)).isEqualTo("未知");
        assertThat(OrderStatus.text(99)).isEqualTo("未知");
    }

    @Test
    @DisplayName("状态常量互不相同，避免误用同一个值")
    void constantsAreDistinct() {
        assertThat(java.util.Set.of(OrderStatus.WAIT_PAY, OrderStatus.PAID,
                OrderStatus.VERIFIED, OrderStatus.CLOSED)).hasSize(4);
        assertThat(OrderStatus.PAY_TIMEOUT_MINUTES).isPositive();
    }
}
