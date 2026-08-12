package com.core.components;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Hello 接口", description = "示例问候接口")
@RestController
public class HelloController {

    @Operation(summary = "打招呼", description = "根据传入的名称返回问候信息")
    @GetMapping("/hello")
    public Map<String, String> sayHello(
            @Parameter(description = "名称", example = "World")
            @RequestParam(value = "name", defaultValue = "World") String name) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello, " + name + "!");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }
}