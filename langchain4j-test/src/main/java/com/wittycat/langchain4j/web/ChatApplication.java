package com.wittycat.langchain4j.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 11:把 LangChain4j 聊天助手暴露成 SSE 流式 REST 接口(Spring Boot 集成)。
 *
 * 运行方式:
 *   mvn -q spring-boot:run            (或 IDE 运行本类的 main)
 * 启动后另开终端测试(README.md 里有完整说明):
 *   curl -N "localhost:8083/chat/stream?message=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8DRAG&sessionId=s1"
 *
 * 【知识点】
 * 1. LangChain4j 核心库是框架无关的纯 Java 库,放进 Spring Boot 只需要:
 *    一个启动类 + 一个 @RestController,没有任何 magic。
 * 2. 端口 8083:仓库内的端口约定是 ai-agent 8080、agentscope-test 8081、
 *    langgraph4j-test 预留 8082,本模块顺延(见 application.yml)。
 * 3. 接口实现在同包 ChatController:TokenStream 回调对接 SseEmitter,
 *    sessionId 记忆(示例 5)+ 流式输出(示例 3)的组合应用。
 */
@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
