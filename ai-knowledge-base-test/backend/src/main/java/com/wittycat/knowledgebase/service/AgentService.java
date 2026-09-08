package com.wittycat.knowledgebase.service;

import com.wittycat.knowledgebase.config.AgentConfig;
import com.wittycat.knowledgebase.dto.*;
import com.wittycat.knowledgebase.rag.RagService;
import com.wittycat.knowledgebase.tool.ToolExecutor;
import com.wittycat.knowledgebase.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final GlmClient glmClient;
    private final ConversationService conversationService;
    private final RagService ragService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;

    /** 固定大小线程池，避免 CachedThreadPool 无限制创建线程导致 OOM */
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        log.info("[Agent] 线程池已关闭");
    }

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，具备以下能力：
            1. 基于知识库资料回答问题（RAG）：用户在界面上传的文档会被自动检索，系统提示中的【资料】即来自知识库
            2. 调用工具执行 Shell 命令、读写文件：工具只能访问工作目录（~/ai-knowledge-base-test-workspace）

            重要：知识库和工作目录是两个独立的地方。用户说"我上传了文档/资料"指的是知识库——
            请对照下方"知识库现有文档"清单确认，已存在就直接基于知识库资料回答，不要让用户重新上传，
            也不要去工作目录找知识库文档。工作目录里的文件只是工具读写的产物，不代表知识库内容。

            回答时请用中文，简洁准确。使用工具前先说明意图，执行后总结结果。
            """;

    public void chatStream(ChatRequest request, SseEmitter emitter) {
        log.info("[Agent] 开始处理对话请求, conversationId={}, enableRag={}, enableTools={}", 
                request.getConversationId(), request.isEnableRag(), request.isEnableTools());
        long startTime = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                Long conversationId = request.getConversationId();
                if (conversationId == null) {
                    log.info("[Agent] 创建新对话");
                    var conv = conversationService.createConversation("新对话");
                    conversationId = conv.getId();
                    log.info("[Agent] 新对话创建成功, conversationId={}", conversationId);
                    sendEvent(emitter, SseEvent.Type.CONVERSATION_CREATED, String.valueOf(conversationId));
                }

                // 保存用户消息
                conversationService.saveMessage(conversationId, "user", request.getMessage());
                conversationService.updateTitle(conversationId, request.getMessage());

                // 构建消息列表（含对话记忆）
                List<ChatMessageDto> messages = buildMessages(conversationId, request);
                log.info("[Agent] 消息列表构建完成, 消息数={}", messages.size());

                // Agent 循环：支持多轮工具调用
                String finalAnswer = runAgentLoop(conversationId, messages, request, emitter);

                // 保存助手回复
                if (finalAnswer != null && !finalAnswer.isBlank()) {
                    conversationService.saveMessage(conversationId, "assistant", finalAnswer);
                    log.info("[Agent] 助手回复已保存, 内容长度={}", finalAnswer.length());
                }

                sendEvent(emitter, SseEvent.Type.DONE, String.valueOf(conversationId));
                emitter.complete();
                log.info("[Agent] 对话处理完成, conversationId={}, 总耗时={}ms", 
                        conversationId, System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("[Agent] 对话处理失败, 耗时={}ms", System.currentTimeMillis() - startTime, e);
                try {
                    sendEvent(emitter, SseEvent.Type.ERROR, e.getMessage());
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    private List<ChatMessageDto> buildMessages(Long conversationId, ChatRequest request) {
        List<ChatMessageDto> messages = new ArrayList<>();

        // System prompt
        StringBuilder systemContent = new StringBuilder(SYSTEM_PROMPT);

        // RAG 检索
        if (request.isEnableRag()) {
            log.info("[Agent] 开始 RAG 检索, query={}", request.getMessage());
            List<String> ragChunks = ragService.retrieve(request.getMessage());
            if (!ragChunks.isEmpty()) {
                log.info("[Agent] RAG 检索到 {} 个相关片段", ragChunks.size());
                systemContent.append("\n\n").append(ragService.buildContextPrompt(ragChunks));
            } else {
                log.info("[Agent] RAG 未检索到相关内容");
            }

            // 知识库文档清单：让模型知道用户上传过哪些文档，避免把"上传"误解为工作目录文件操作
            List<String> docNames = ragService.listDocuments().stream()
                    .map(d -> d.getFilename())
                    .filter(Objects::nonNull)
                    .toList();
            if (!docNames.isEmpty()) {
                systemContent.append("\n\n知识库现有文档：").append(String.join("、", docNames));
            }
        }

        messages.add(ChatMessageDto.builder().role("system").content(systemContent.toString()).build());

        // 对话记忆
        List<ChatMessageDto> memory = conversationService.loadMemory(conversationId);
        messages.addAll(memory);
        log.info("[Agent] 加载对话记忆 {} 条", memory.size());

        return messages;
    }

    private String runAgentLoop(Long conversationId, List<ChatMessageDto> messages,
                                ChatRequest request, SseEmitter emitter) throws IOException {
        StringBuilder fullAnswer = new StringBuilder();
        int iterations = 0;

        while (iterations < agentConfig.getMaxToolIterations()) {
            iterations++;
            log.info("[Agent] Agent 循环第 {} 轮开始", iterations);

            ChatCompletionRequest apiRequest = ChatCompletionRequest.builder()
                    .messages(messages)
                    .stream(true)
                    .build();

            if (request.isEnableTools()) {
                apiRequest.setTools(toolRegistry.getToolDefinitions());
                apiRequest.setToolChoice("auto");
            }

            // 收集流式响应
            StringBuilder contentBuffer = new StringBuilder();
            Map<Integer, ToolCallAccumulator> toolCallAccumulators = new HashMap<>();

            glmClient.streamChat(apiRequest, chunk -> {
                try {
                    if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) return;
                    ChatCompletionChunk.Choice choice = chunk.getChoices().get(0);
                    ChatCompletionChunk.Delta delta = choice.getDelta();
                    if (delta == null) return;

                    // 文本内容
                    if (delta.getContent() != null) {
                        contentBuffer.append(delta.getContent());
                        sendEvent(emitter, SseEvent.Type.CONTENT, delta.getContent());
                    }

                    // 工具调用增量
                    if (delta.getToolCalls() != null) {
                        for (ChatCompletionChunk.ToolCallDelta tc : delta.getToolCalls()) {
                            int idx = tc.getIndex() != null ? tc.getIndex() : 0;
                            ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(idx, k -> new ToolCallAccumulator());
                            if (tc.getId() != null) acc.id = tc.getId();
                            if (tc.getFunction() != null) {
                                if (tc.getFunction().getName() != null) acc.name = tc.getFunction().getName();
                                if (tc.getFunction().getArguments() != null) acc.arguments.append(tc.getFunction().getArguments());
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("[Agent] SSE 发送失败", e);
                }
            });

            // 检查是否有工具调用
            if (!toolCallAccumulators.isEmpty()) {
                log.info("[Agent] 检测到 {} 个工具调用", toolCallAccumulators.size());
                // 构建 assistant 消息（含 tool_calls）
                List<ChatMessageDto.ToolCallDto> toolCalls = new ArrayList<>();
                for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
                    toolCalls.add(ChatMessageDto.ToolCallDto.builder()
                            .id(acc.id)
                            .type("function")
                            .function(ChatMessageDto.ToolCallDto.FunctionCall.builder()
                                    .name(acc.name)
                                    .arguments(acc.arguments.toString())
                                    .build())
                            .build());
                }

                ChatMessageDto assistantMsg = ChatMessageDto.builder()
                        .role("assistant")
                        .content(contentBuffer.isEmpty() ? null : contentBuffer.toString())
                        .toolCalls(toolCalls)
                        .build();
                messages.add(assistantMsg);

                // 保存 assistant 消息（含 tool_calls）
                try {
                    conversationService.saveMessage(conversationId, "assistant",
                            contentBuffer.toString(), objectMapper.writeValueAsString(toolCalls), null);
                } catch (Exception e) {
                    log.warn("[Agent] 保存工具调用消息失败", e);
                }

                // 执行每个工具并添加 tool 响应消息
                for (ChatMessageDto.ToolCallDto tc : toolCalls) {
                    String toolName = tc.getFunction().getName();
                    log.info("[Agent] 执行工具: {}", toolName);
                    sendEvent(emitter, SseEvent.Type.TOOL_CALL, toolName);

                    String result = toolExecutor.execute(toolName, tc.getFunction().getArguments());
                    log.info("[Agent] 工具执行完成: {}, 结果长度={}", toolName, result != null ? result.length() : 0);
                    sendEvent(emitter, SseEvent.Type.TOOL_RESULT, toolName + ": " + result);

                    messages.add(ChatMessageDto.builder()
                            .role("tool")
                            .content(result)
                            .toolCallId(tc.getId())
                            .build());

                    conversationService.saveMessage(conversationId, "tool", result, null, tc.getId());
                }

                // 继续循环，让 LLM 基于工具结果生成最终回答
                log.info("[Agent] 继续下一轮循环，基于工具结果生成回答");
                continue;
            }

            // 无工具调用，返回最终回答
            log.info("[Agent] 无工具调用，返回最终回答");
            fullAnswer.append(contentBuffer);
            break;
        }

        log.info("[Agent] Agent 循环结束, 共执行 {} 轮", iterations);
        return fullAnswer.toString();
    }

    private void sendEvent(SseEmitter emitter, SseEvent.Type type, String content) throws IOException {
        SseEvent event = SseEvent.builder().type(type).content(content).build();
        emitter.send(SseEmitter.event()
                .name(type.name())
                .data(objectMapper.writeValueAsString(event)));
    }

    private static class ToolCallAccumulator {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();
    }
}
