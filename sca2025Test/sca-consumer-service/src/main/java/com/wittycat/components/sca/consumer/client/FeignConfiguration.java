package com.wittycat.components.sca.consumer.client;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by chenxun.
 * Date: 2026/7/14 17:07
 * Description:
 */
@Configuration
public class FeignConfiguration {

    /**
     * 禁用Feign默认重试器
     */
    @Bean(name = "feignRetryer")
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}