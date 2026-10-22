package com.tfsh.dto;

import lombok.Data;

/**
 * 刷新令牌请求体。
 */
@Data
public class RefreshFormDTO {
    private String refreshToken;
}
