package com.wittycat.components.sca.gateway;

import com.wittycat.components.sca.common.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(-10)
public class AuthFilterConfig implements GlobalFilter {

    private final JwtUtil jwtUtil;

    // 构造注入，无需@Autowired
    public AuthFilterConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 白名单直接放行
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        // 获取token头
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange.getResponse(), "缺少Token", HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring(7);

        // 校验token
        if (!jwtUtil.verify(token)) {
            return writeError(exchange.getResponse(), "Token无效或过期", HttpStatus.UNAUTHORIZED);
        }

        // 解析用户信息，透传给下游微服务
        Claims claims = jwtUtil.getClaims(token);
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        // 新增请求头，下游服务直接读取
        ServerHttpRequest newReq = request.mutate()
                .header("X-Login-UserId", userId)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(newReq).build());
    }

    // 放行白名单：登录、注册、健康检查
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/user/login"
    );

    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(path::contains);
    }

    // 返回统一401 json
    private Mono<Void> writeError(ServerHttpResponse resp, String msg, HttpStatus status) {
        resp.setStatusCode(status);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"code\":401,\"msg\":\"" + msg + "\"}";
        DataBuffer buffer = resp.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Flux.just(buffer));
    }
}