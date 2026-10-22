package com.tfsh.order;

/**
 * 订单状态常量 + 文案。
 * 单独抽出来，避免各处写魔法数字 1/2/3/4。
 */
public final class OrderStatus {

    /** 待支付 */
    public static final int WAIT_PAY = 1;
    /** 已支付（待核销） */
    public static final int PAID = 2;
    /** 已核销 */
    public static final int VERIFIED = 3;
    /** 已关闭（用户取消 / 超时未支付） */
    public static final int CLOSED = 4;

    /** 下单后多少分钟未支付自动关闭（商品单与秒杀券单共用） */
    public static final int PAY_TIMEOUT_MINUTES = 15;

    private OrderStatus() {
    }

    public static String text(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case WAIT_PAY -> "待支付";
            case PAID -> "待核销";
            case VERIFIED -> "已核销";
            case CLOSED -> "已关闭";
            default -> "未知";
        };
    }
}
