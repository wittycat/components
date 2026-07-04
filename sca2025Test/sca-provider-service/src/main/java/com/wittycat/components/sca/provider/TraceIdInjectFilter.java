//package com.wittycat.components.sca.provider;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.slf4j.MDC;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//
///**
// * Created by chenxun.
// * Date: 2026/7/4 14:20
// * Description:
// */
//@Component
//public class TraceIdInjectFilter extends OncePerRequestFilter {
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain chain) throws ServletException, IOException {
//        try {
//            // 从请求头中读取 B3 标准头
//            String traceId = request.getHeader("X-B3-TraceId");
//            String spanId = request.getHeader("X-B3-SpanId");
//
//            if (traceId != null) {
//                MDC.put("traceId", traceId);
//            }
//            if (spanId != null) {
//                MDC.put("spanId", spanId);
//            }
//
//            chain.doFilter(request, response);
//        } finally {
//            // 请求结束务必清理，防止线程池复用导致污染
//            MDC.remove("traceId");
//            MDC.remove("spanId");
//        }
//    }
//
//
//}