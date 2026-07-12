package com.wittycat.components.sca.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Created by chenxun.
 * Date: 2026/7/1 19:02
 * Description:
 */
@Slf4j
@Component
@Order(-100)
public class LogFilterConfig implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // 记录请求开始
        log.info("【请求开始】{} {}，参数：{}", method, path, exchange.getRequest().getQueryParams());

        // 在响应结束后记录
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int status = exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : 0;
            log.info("【请求结束】{} {}，状态码：{}，耗时：{} ms", method, path, status, duration);
        }));
    }
}