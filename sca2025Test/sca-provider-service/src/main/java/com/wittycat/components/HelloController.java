package com.wittycat.components;

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
public class HelloController {

    @Value("${server.port}")
    private int port;

    @GetMapping("/hello")
    public String sayHello(@RequestParam String name) {
        return "Hello " + name + "，来自服务提供者 (端口 " + port + ")";
    }
}