package com.tfsh.config;

import com.tfsh.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把异常统一包装成 Result.fail，
 * 前端可以稳定地拿到 { success:false, errorMsg } 结构。
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("服务器异常", e);
        return Result.fail(e.getMessage() == null ? "服务器繁忙，请稍后再试" : e.getMessage());
    }
}
