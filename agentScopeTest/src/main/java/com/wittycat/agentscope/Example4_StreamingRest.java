package com.wittycat.agentscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 4:把 Agent 暴露成 SSE 流式 REST 接口(Spring Boot 集成)。
 *
 * 运行方式:
 *   mvn -q spring-boot:run            (或 IDE 运行本类的 main)
 * 启动后另开终端测试(README.md 里有完整说明):
 *   curl -N -X POST localhost:8081/api/agent/chat \
 *        -H 'Content-Type: application/json' \
 *        -d '{"message":"北京和上海今天哪个更热?","sessionId":"s1"}'
 *
 * 【知识点】
 * 1. AgentScope 核心库是框架无关的纯 Java 库,放进 Spring Boot 只需要:
 *      启动类 + 一个 @RestController,没有任何 magic。
 * 2. 对外接口推荐基于 streamEvents() 的事件流做——SSE(Server-Sent Events)天然对应
 *    "打字机效果",前端一行 EventSource/fetch 流就能消费。
 * 3. sessionId 由客户端传入:同一个 sessionId 连续请求,Agent 会记得之前的对话
 *    (原理同示例 3),这就是"多轮聊天接口"的雏形。
 * 4. 接口实现在 web/AgentChatController.java,建议对照阅读。
 */
@SpringBootApplication
public class Example4_StreamingRest {

    public static void main(String[] args) {
        SpringApplication.run(Example4_StreamingRest.class, args);
    }
}
