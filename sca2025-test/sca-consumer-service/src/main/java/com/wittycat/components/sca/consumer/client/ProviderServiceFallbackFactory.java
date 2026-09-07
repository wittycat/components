package com.wittycat.components.sca.consumer.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Created by chenxun.
 * Date: 2026/7/14 17:18
 * Description:
 */
@Component
@Slf4j
public class ProviderServiceFallbackFactory implements FallbackFactory<ProviderService> {

    @Override
    public ProviderService create(Throwable cause) {
        return new ProviderService() {
            @Override
            public String sayHello(String name) {
                return "";
            }

            @Override
            public String deduct(int productId,int userId, int productNum) {
                log.error("调用订单服务查询异常，productId:"+productId+", userId="+userId+" ，原因:{}", cause);
                // 降级逻辑：返回缓存/空数据/友好提示
                return "订单服务暂时不可用，请稍后重试";
            }

            @Override
            public String atDeduct(int productId,int userId, int productNum) {
                log.error("调用订单服务查询异常，productId:"+productId+", userId="+userId+" ，原因:{}", cause);
                return "创建订单服务繁忙";
            }
        };
    }
}