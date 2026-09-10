package com.wittycat.springai.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE 流式聊天接口:GET /chat/stream?message=...&sessionId=...
 *
 * 【知识点】
 * 1. 注入的是 ChatClient.Builder(自动配置提供):defaultSystem 设全局人设,
 *    defaultAdvisors 挂记忆 Advisor——builder 模式把"全局配置"和"单次请求参数"分开。
 * 2. 流式对接:示例 3 的 Flux 不需要 block,直接作为 Controller 返回值并声明
 *    produces = TEXT_EVENT_STREAM,Spring MVC 就会逐帧推给客户端(Reactor 桥接)。
 *    每个 token 一帧 data:,末尾追加一帧 [DONE] 哨兵,前端以此收尾(concatWith)。
 * 3. 会话记忆(示例 5)在这里发挥 Web 价值:同一个 sessionId 连续请求,助手记得上文;
 *    不同 sessionId 互不干扰——这就是"多用户多轮聊天接口"的雏形。
 * 4. SSE 天然单向(服务器 → 客户端),聊天回复正是这个方向;上传文件等复杂交互再考虑 WebSocket。
 * 5. 生产化提示:超时与取消(Spring MVC 会在客户端断开时取消 Flux)、
 *    记忆持久化(MessageWindowChatMemory 换 Redis 实现)、敏感词/审计 Advisor 都是上线前要补的作业。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("你是一个网页聊天助手,用简洁的中文回答问题。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder().maxMessages(20).build())
                        .build())
                .build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String message,
                                                @RequestParam(defaultValue = "web-default") String sessionId) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .map(token -> ServerSentEvent.builder(token).build())
                // [DONE] 哨兵帧,前端据此判断流结束
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()));
    }
}
