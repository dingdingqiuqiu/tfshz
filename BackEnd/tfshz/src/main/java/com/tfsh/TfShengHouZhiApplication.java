package com.tfsh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.tfsh.mapper")
@SpringBootApplication
public class TfShengHouZhiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TfShengHouZhiApplication.class, args);
    }
}