package com.tfsh.config;

import com.tfsh.utils.JwtUtils;
import com.tfsh.utils.LoginInterceptor;
import com.tfsh.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class MvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtUtils jwtUtils;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 开发环境：直接从后端提供 /imgs/ 下的静态文件
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations("file:D:/1devCode/tfshz/FrontEnd/nginx-1.18.0/html/tfsh/imgs/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order(0) 先执行：负责解析 token、填充 UserHolder（放行所有请求）
        registry.addInterceptor(new RefreshTokenInterceptor(jwtUtils)).order(0);
        // order(1) 后执行：负责拦截”未登录”的请求
        // 注意：Spring Boot 3 默认用 PathPatternParser，它不认 `\d+` 这种“整段正则”，
        // 只能写成 `{id:正则}`；写成 "/user/\\d+" 会按字面量匹配，等于白名单从没生效过。
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/imgs/**",
                        "/types/**",
                        "/blog/hot",
                        // 文章详情、点赞列表是公开页面：不加入白名单的话，
                        // 未登录用户点进任何一篇文章都会 401，前端拦截器随后把人踢到登录页。
                        "/blog/{id:[0-9]+}",
                        "/blog/likes/{id:[0-9]+}",
                        "/blog-comments/of/**",
                        "/blog-comments/reply/**",
                        "/user/code",
                        "/user/login",
                        "/user/refresh",
                        // 他人主页公开；/user/me、/user/logout 不匹配数字，仍然需要登录
                        "/user/{id:[0-9]+}"
                ).order(1);
    }
}
