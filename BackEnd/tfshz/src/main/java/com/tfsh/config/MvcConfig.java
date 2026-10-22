package com.tfsh.config;

import com.tfsh.utils.JwtUtils;
import com.tfsh.utils.LoginInterceptor;
import com.tfsh.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class MvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtUtils jwtUtils;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order(0) 先执行：负责解析 token、填充 UserHolder（放行所有请求）
        registry.addInterceptor(new RefreshTokenInterceptor(jwtUtils)).order(0);
        // order(1) 后执行：负责拦截“未登录”的请求
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/user/refresh"      // 刷新接口凭 refresh token 访问，必须放行
                ).order(1);
    }
}
