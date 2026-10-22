package com.tfsh.utils;

import com.tfsh.dto.UserDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类：负责 access / refresh 两类令牌的签发与校验。
 *
 * 原理回顾：
 *   JWT = Base64URL(header) + "." + Base64URL(payload) + "." + HMACSHA256(secret, 前两段)
 *   验签 = 用同一个 secret 重算一遍签名再比对；
 *   任何对 header/payload 的篡改都会导致签名对不上而被拒收。
 */
@Component
public class JwtUtils {

    /** 签发者标识，校验时强制检查，防止别处签的 token 混进来 */
    private static final String ISSUER = "tfshz";

    /** 签名密钥（HS256 要求 >= 32 字节，启动时由 secret 生成） */
    private SecretKey key;

    @Value("${tfsh.jwt.secret}")
    private String secret;

    /** access 有效期（毫秒），默认 30 分钟 */
    @Value("${tfsh.jwt.access-ttl:1800000}")
    private long accessTtl;

    /** refresh 有效期（毫秒），默认 7 天 */
    @Value("${tfsh.jwt.refresh-ttl:604800000}")
    private long refreshTtl;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 access token。
     * payload 只放“认证需要的最小信息”，不放手机号等敏感字段（JWT 是编码不是加密）。
     */
    public String createAccessToken(UserDTO userDTO) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userDTO.getId()))   // sub = 用户 id
                .id(UUID.randomUUID().toString())           // jti = 本 token 的唯一编号
                .claim("nickName", userDTO.getNickName())
                .claim("icon", userDTO.getIcon())
                .claim("type", "access")                    // 防止 access/refresh 混用
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTtl))
                .signWith(key)                              // 默认 HS256
                .compact();
    }

    /** 签发 refresh token：payload 只放 id + 编号，最小化暴露 */
    public String createRefreshToken(Long userId) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTtl))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 token。
     * 签名错误 / 已过期 / 签发者不符 / 类型不符 → 抛出异常，由调用方决定如何处理。
     */
    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)          // 用同一个 secret 验签
                .requireIssuer(ISSUER)    // 校验签发者
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("token 类型错误");
        }
        return claims;
    }

    public long getAccessTtl() { return accessTtl; }
    public long getRefreshTtl() { return refreshTtl; }
}
