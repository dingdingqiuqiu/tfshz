package com.tfsh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录 / 刷新接口的统一返回结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResultDTO {
    /** 短期凭证：30 分钟有效，每次业务请求携带 */
    private String accessToken;
    /** 长期凭证：7 天有效，仅用于换取新的 accessToken（用完即换，旧的一律作废） */
    private String refreshToken;
    /** 用户展示信息，前端可直接渲染，不必再请求 /user/me */
    private UserDTO user;
}
