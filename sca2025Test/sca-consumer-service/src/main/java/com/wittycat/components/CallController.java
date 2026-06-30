package com.wittycat.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by chenxun.
 * Date: 2026/6/30 21:46
 * Description:
 */
@RestController
public class CallController {

    @Autowired
    private HelloFeignClient helloFeignClient;

    @GetMapping("/call")
    public String callProvider(@RequestParam String name) {
        return helloFeignClient.sayHello(name);
    }
}