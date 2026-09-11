package com.wittycat.components.sca.gateway;

import com.wittycat.components.sca.common.NacosClientCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Created by chenxun.
 * Date: 2026/7/1 18:11
 * Description:
 */
@SpringBootApplication(scanBasePackages = "com.wittycat.components.sca")
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        NacosClientCache.init();
        SpringApplication.run(GatewayApplication.class, args);
    }
}