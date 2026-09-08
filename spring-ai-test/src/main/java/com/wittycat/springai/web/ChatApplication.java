package com.wittycat.springai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 9:把 Spring AI 聊天助手暴露成 SSE 流式 REST 接口(Spring Boot 集成)。
 *
 * 运行方式:
 *   mvn -q spring-boot:run            (或 IDE 运行本类的 main)
 * 启动后另开终端测试(README.md 里有完整说明):
 *   curl -N "localhost:8084/chat/stream?message=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8DRAG&sessionId=s1"
 *
 * 【知识点】
 * 1. Spring AI 是"长在 Spring 里"的框架,和 langchain4j-test 示例 11 的最大区别在装配方式:
 *    starter 自动配置读了 application.yml 里的 spring.ai.openai.*(见本模块的 application.yml,
 *    它用 ${glm.xxx} 占位符和我们统一的 glm.* 约定打通),容器里就有了 ChatModel /
 *    EmbeddingModel / ChatClient.Builder 三个 Bean,业务代码直接注入,零装配代码。
 * 2. 端口 8084:仓库内的端口约定是 ai-agent 8080、agentscope-test 8081、
 *    langgraph4j-test 预留 8082、langchain4j-test 8083,本模块顺延(见 application.yml)。
 * 3. 接口实现在同包 ChatController:ChatClient.stream() 的 Flux 直接作为返回值,
 *    Spring MVC 借 Reactor 桥接自动推 SSE(agentscope-test 同款风格;
 *    langchain4j-test 是 TokenStream 回调 + SseEmitter,两条路线可对照)。
 */
@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
