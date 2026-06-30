package com.core.components;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxun.
 * Date: 2026/6/29 23:16
 * Description:
 */
@RestController
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> sayHello(
            @RequestParam(value = "name", defaultValue = "World") String name) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello, " + name + "!");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }
}