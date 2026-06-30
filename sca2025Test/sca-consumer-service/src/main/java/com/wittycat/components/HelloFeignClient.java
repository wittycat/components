package com.wittycat.components;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Created by chenxun.
 * Date: 2026/6/30 21:46
 * Description:
 */
@FeignClient(name = "provider-service")
public interface HelloFeignClient {

    @GetMapping("/hello")
    String sayHello(@RequestParam("name") String name);
}