package com.wittycat.components.sca.provider;

import com.wittycat.components.sca.common.NacosClientCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Created by chenxun.
 * Date: 2026/6/30 21:36
 * Description:
 */
@SpringBootApplication(scanBasePackages = "com.wittycat.components.sca")
@EnableDiscoveryClient
public class ProviderApplication {
    public static void main(String[] args) {
        NacosClientCache.init();
        SpringApplication.run(ProviderApplication.class, args);
    }
}