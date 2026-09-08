package com.wittycat.langchain4j.web;

import com.wittycat.langchain4j.GlmModels;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 流式聊天接口:GET /chat/stream?message=...&sessionId=...
 *
 * 【知识点】
 * 1. TokenStream(示例 3 的回调风格)对接 Spring MVC 的 SseEmitter,是最轻量的组合:
 *      - onPartialResponse:每到一个 token,emitter.send() 推一帧,浏览器里就是打字机;
 *      - onCompleteResponse:推一帧 [DONE] 哨兵再 complete(),前端以此收尾;
 *      - onError:completeWithError,让客户端感知断流。
 *    (agentscope-test 的接口返回 Flux<ServerSentEvent>,那是 Reactor 风格;这里
 *     不引入额外依赖,回调 + SseEmitter 就够了。)
 * 2. 记忆(示例 5)在这里发挥了 Web 价值:同一个 sessionId 连续请求,助手记得上文;
 *    不同 sessionId 互不干扰——这就是"多用户多轮聊天接口"的雏形。
 * 3. SSE 天然单向(服务器 → 客户端),聊天回复正是这个方向;若要上传文件等复杂交互,
 *    再考虑 WebSocket。
 * 4. 生产化提示:超时(120s)、客户端中途断开(IOError -> completeWithError)、
 *    记忆的持久化(ChatMemoryStore 换 Redis)都是上线前要补的作业。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    /** 流式助手接口:返回 TokenStream,带 @MemoryId 会话记忆 */
    interface ChatAssistant {

        @SystemMessage("你是一个网页聊天助手,用简洁的中文回答问题。")
        TokenStream chat(@MemoryId String sessionId, @UserMessage String message);
    }

    private final ChatAssistant assistant;

    public ChatController() {
        this.assistant = AiServices.builder(ChatAssistant.class)
                .streamingChatModel(GlmModels.createStreamingModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(20).build())
                .build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String message,
                             @RequestParam(defaultValue = "web-default") String sessionId) {
        // 120s 超时兜底,防止模型卡死时连接悬挂
        SseEmitter emitter = new SseEmitter(120_000L);

        assistant.chat(sessionId, message)
                .onPartialResponse(token -> {
                    try {
                        emitter.send(token);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(throwable -> emitter.completeWithError(throwable))
                .start();

        return emitter;
    }
}
