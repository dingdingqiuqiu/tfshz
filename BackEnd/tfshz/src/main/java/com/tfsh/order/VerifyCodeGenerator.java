package com.tfsh.order;

import java.security.SecureRandom;

/**
 * 核销码生成器。
 *
 * 设计要点：
 *   1. 字符集去掉 I/O/0/1，避免口述和手输看混；
 *   2. 带类型前缀（P 商品单 / V 券单），这样统一核销接口**不用扫两张表**就能定位；
 *   3. 随机而非自增：核销码出现在二维码里，可被枚举就意味着能被白吃。
 */
public final class VerifyCodeGenerator {

    private static final char[] CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 9;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 商品订单前缀 */
    public static final char PREFIX_PRODUCT = 'P';
    /** 秒杀券订单前缀 */
    public static final char PREFIX_VOUCHER = 'V';

    private VerifyCodeGenerator() {
    }

    public static String next(char prefix) {
        StringBuilder sb = new StringBuilder(1 + LENGTH);
        sb.append(prefix);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }
}
