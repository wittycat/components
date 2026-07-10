package com.wittycat.components.sca.consumer.controller;

import com.wittycat.components.sca.consumer.client.ProviderService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by chenxun.
 * Date: 2026/6/30 21:46
 * Description:
 */
@RestController
@Slf4j
public class ConsumerTestController {

    @Autowired
    private ProviderService providerService;
    @Autowired
    private DiscoveryClient discoveryClient;

    @GetMapping("/call")
    public String callProvider(@RequestParam String name) {
        // 获取指定服务所有在线实例
        List<ServiceInstance> instances = discoveryClient.getInstances("provider-service");
        for (ServiceInstance serviceInstance:instances) {
            log.info("instances_info  serviceId={}  host={} ",serviceInstance.getServiceId(),serviceInstance.getHost());
        }
        String result = providerService.sayHello(name);
        log.info("name={}, result={}  ",name,result);
        return result;
    }

    @GetMapping("/test-log")
    public String testLog() {
        // 手动塞入测试数据
        MDC.put("traceId", "TEST-12345");
        MDC.put("spanId", "TEST-67890");

        log.info("这是使用 @Slf4j 打印的测试日志");

        MDC.clear(); // 清除，防止污染其他请求
        return "OK";
    }
}