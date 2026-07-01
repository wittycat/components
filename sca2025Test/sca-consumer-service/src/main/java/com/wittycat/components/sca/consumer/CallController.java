package com.wittycat.components.sca.consumer;

import lombok.extern.slf4j.Slf4j;
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
public class CallController {

    @Autowired
    private HelloFeignClient helloFeignClient;
    @Autowired
    private DiscoveryClient discoveryClient;

    @GetMapping("/call")
    public String callProvider(@RequestParam String name) {
        // 获取指定服务所有在线实例
        List<ServiceInstance> instances = discoveryClient.getInstances("provider-service");
        for (ServiceInstance serviceInstance:instances) {
            log.info("instances_info  serviceId={}  host={} ",serviceInstance.getServiceId(),serviceInstance.getHost());
        }

        String result = helloFeignClient.sayHello(name);
        log.info("name={}, result={}  ",name,result);
        return result;
    }
}