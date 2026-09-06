package com.agent.controller;

import com.agent.dto.ChatRequest;
import com.agent.entity.Conversation;
import com.agent.entity.Message;
import com.agent.service.AgentService;
import com.agent.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final AgentService agentService;
    private final ConversationService conversationService;

    /** SSE 流式对话 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("[Chat] 收到对话请求, conversationId={}, message长度={}",
                request.getConversationId(),
                request.getMessage() != null ? request.getMessage().length() : 0);
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> {
            log.warn("[Chat] SSE 连接超时");
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("[Chat] SSE 连接异常", e);
            emitter.complete();
        });
        agentService.chatStream(request, emitter);
        return emitter;
    }

    /** 获取对话列表 */
    @GetMapping("/conversations")
    public List<Conversation> listConversations() {
        log.info("[Chat] 查询对话列表");
        return conversationService.listConversations();
    }

    /** 创建新对话 */
    @PostMapping("/conversations")
    public Conversation createConversation(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        log.info("[Chat] 创建对话, title={}", title);
        return conversationService.createConversation(title);
    }

    /** 获取对话消息历史 */
    @GetMapping("/conversations/{id}/messages")
    public List<Message> getMessages(@PathVariable Long id) {
        log.info("[Chat] 查询对话消息, conversationId={}", id);
        return conversationService.getMessages(id);
    }

    /** 删除对话 */
    @DeleteMapping("/conversations/{id}")
    public void deleteConversation(@PathVariable Long id) {
        log.info("[Chat] 删除对话, conversationId={}", id);
        conversationService.deleteConversation(id);
    }
}
