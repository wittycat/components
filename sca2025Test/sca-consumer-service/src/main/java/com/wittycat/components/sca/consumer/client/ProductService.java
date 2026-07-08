package com.wittycat.components.sca.consumer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用于访问Product服务的feign
 */
@FeignClient("provider-service")
public interface ProductService {

    @RequestMapping(value = "/product/deduct")
    String deduct(@RequestParam("productId") int productId,@RequestParam("userId") int userId, @RequestParam("productNum") int productNum);
}
