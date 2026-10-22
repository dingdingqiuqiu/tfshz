package com.tfsh.utils;

import cn.hutool.core.util.StrUtil;
import com.tfsh.dto.UserDTO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 令牌解析拦截器（order 小，先执行）：
 *   职责只有一个 —— 从请求头解析 access token，验签成功后把用户装进 ThreadLocal。
 *   无论是否携带 token 都放行，真正的“拦截”由 LoginInterceptor 完成。
 *
 * 为什么 access 在这里只验签、不查 Redis：
 *   短有效期本身就是撤销机制 —— 被踢掉的 token 最多再活 30 分钟；
 *   换来的是每个请求零 Redis 访问，登录后的吞吐不再受 Redis 制约。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    public RefreshTokenInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(header) && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtils.parse(header.substring(7), "access");
                UserDTO userDTO = new UserDTO();
                userDTO.setId(Long.valueOf(claims.getSubject()));
                userDTO.setNickName(claims.get("nickName", String.class));
                userDTO.setIcon(claims.get("icon", String.class));
                UserHolder.setUser(userDTO);
            } catch (Exception e) {
                // 过期 / 伪造 / 格式错误：不在这里拦截，交给 LoginInterceptor 判定
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清理 ThreadLocal：
        // 1) 防内存泄漏；2) Tomcat 线程池会复用线程，不清理会导致“串号”（张三拿到李四的用户）
        UserHolder.remove();
    }
}
