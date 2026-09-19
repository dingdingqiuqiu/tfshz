package com.tfsh.config;

import cn.hutool.core.text.replacer.StrReplacer;
import com.tfsh.utils.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(new LoginInterceptor()).
//                excludePathPatterns(
//                        "/shop/**",
//                        "/voucher/**",
//                        "/shop-type/**",
//                        "/upload/**",
//                        "/blog/hot",
//                        "/user/code",
//                        "/user/login"
//                ).order(1);
//        registry.addInterceptor()
    }
}