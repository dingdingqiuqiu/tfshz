package com.tfsh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling：开启 Spring Task，用于「超时未支付订单自动关闭」的定时任务 */
@EnableScheduling
@MapperScan("com.tfsh.mapper")
@SpringBootApplication
public class TfShengHouZhiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TfShengHouZhiApplication.class, args);
    }
}
