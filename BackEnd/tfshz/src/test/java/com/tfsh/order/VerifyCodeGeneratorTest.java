package com.tfsh.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核销码生成器：格式、字符集、唯一性。
 * 这些是"能被扫、能被念、不会被猜"的底线，属于纯函数，适合单测兜住。
 */
class VerifyCodeGeneratorTest {

    private static final String ALLOWED = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    @Test
    @DisplayName("商品码以 P 开头、券码以 V 开头，长度固定 10")
    void prefixAndLength() {
        String product = VerifyCodeGenerator.next(VerifyCodeGenerator.PREFIX_PRODUCT);
        String voucher = VerifyCodeGenerator.next(VerifyCodeGenerator.PREFIX_VOUCHER);

        assertThat(product).hasSize(10).startsWith("P");
        assertThat(voucher).hasSize(10).startsWith("V");
    }

    @Test
    @DisplayName("字符集不含容易看混的 I/O/0/1")
    void charsetExcludesConfusingChars() {
        for (int i = 0; i < 200; i++) {
            String code = VerifyCodeGenerator.next(VerifyCodeGenerator.PREFIX_PRODUCT);
            for (char c : code.substring(1).toCharArray()) {
                assertThat(ALLOWED).contains(String.valueOf(c));
            }
        }
    }

    @Test
    @DisplayName("批量生成基本不重复（随机性冒烟检查）")
    void codesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            codes.add(VerifyCodeGenerator.next(VerifyCodeGenerator.PREFIX_VOUCHER));
        }
        // 9 位 32 进制空间极大，2000 次抽样出现碰撞的概率极低
        assertThat(codes).hasSize(2_000);
    }
}
