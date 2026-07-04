//package com.wittycat.components.sca.gateway;
//
//import brave.Tracer;
//import io.micrometer.observation.Observation;
//import io.micrometer.observation.ObservationHandler;
//import io.micrometer.observation.ObservationRegistry;
//import org.slf4j.MDC;
//import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * Created by chenxun.
// * Date: 2026/7/4 02:32
// * Description:
// */
//@Configuration
//public class ObservationConfig {
//
//    private final Tracer tracer;
//
//    public ObservationConfig(Tracer tracer) {
//        this.tracer = tracer;
//    }
//
//    @Bean
//    public ObservationRegistryCustomizer<ObservationRegistry> observationRegistryCustomizer() {
//        return registry ->
//                registry.observationConfig().observationHandler(new MdcObservationHandler());
//    }
//
//    // 自定义的 ObservationHandler，用于将 traceId 和 spanId 放入 MDC
//    private class MdcObservationHandler implements ObservationHandler<Observation.Context> {
//
//        @Override
//        public void onStart(Observation.Context context) {
//            // 在观察开始时，从 Tracer 获取当前 Span
//            var currentSpan = tracer.currentSpan();
//            if (currentSpan != null) {
//                // 将 traceId 和 spanId 放入 MDC
//                MDC.put("traceId", String.valueOf(currentSpan.context().traceId()));
//                MDC.put("spanId", String.valueOf(currentSpan.context().spanId()));
//            }
//
//            MDC.put("traceId", "sdcdcsc11");
//            MDC.put("spanId", "sdcsd222");
//        }
//
//        @Override
//        public void onStop(Observation.Context context) {
//            // 在观察结束时，清理 MDC，防止上下文串扰
//            MDC.remove("traceId");
//            MDC.remove("spanId");
//        }
//
//        @Override
//        public boolean supportsContext(Observation.Context context) {
//            // 表示此处理器支持所有类型的 Context
//            return true;
//        }
//    }
//}