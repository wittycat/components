package com.core;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by chenxun.
 * Date: 2018/5/14 上午12:44
 * Description:
 */
@SpringBootApplication
@MapperScan("com.core.userinfo.infrastructure.persistence")
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
