package com.wittycat.components.sca.consumer;

import feign.RequestInterceptor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by chenxun.
 * Date: 2026/7/4 14:31
 * Description:
 */
@Configuration
public class FeignConfig {

    /**
     * 为什么 Gateway 和 Consumer 之前自动有，Provider 却需要手动传？
     * Gateway → Consumer：Gateway 是基于 WebClient（响应式），它的 ExchangeFilterFunction 自动处理了 B3 头的传递，
     * Spring Cloud 对此支持非常完善。
     *
     * Consumer → Provider：Consumer 用的是 Feign（同步 HTTP 客户端）。Feign 的自动配置相对复杂，
     * 容易因为用户自定义配置而失效。你遇到的就是这种情况。
     *
     *
     * @param tracer
     * @return
     */
    @Bean
    public RequestInterceptor traceIdRequestInterceptor(Tracer tracer) {
        return template -> {
            // 1. 获取当前请求的 Span
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                // 2. 手动将 B3 标准请求头添加到 Feign 请求中
                template.header("X-B3-TraceId", currentSpan.context().traceId());
                template.header("X-B3-SpanId", currentSpan.context().spanId());
                // 3. 如果有采样标记，也一并传递（可选，但建议加上）
                template.header("X-B3-Sampled", "1");
            }
        };
    }
}