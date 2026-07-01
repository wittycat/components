package com.wittycat.components.sca.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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
public class GlobalLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        log.info("网关接收请求：{} {}", method, path);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;   // 优先级最高
    }

}