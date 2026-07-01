package com.wittycat.components.sca.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by chenxun.
 * Date: 2026/6/30 21:37
 * Description:
 */
@RestController
@Slf4j
public class HelloController {

    @Value("${server.port}")
    private int port;

    @GetMapping("/hello")
    public String sayHello(@RequestParam String name) {
        String result = "Hello " + name + "，来自服务提供者 (端口 " + port + ")";
        log.info("处理完成，返回结果: {}", result);
        return result;
    }
}