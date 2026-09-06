package com.agent.service;

import com.agent.config.GlmConfig;
import com.agent.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlmClient {

    private final GlmConfig glmConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 流式调用 GLM Chat Completions API（SSE）
     */
    public void streamChat(ChatCompletionRequest request, Consumer<ChatCompletionChunk> onChunk) {
        log.info("[GLM API] 开始流式调用 chat/completions, model={}, messages={}",
                request.getModel(), request.getMessages() != null ? request.getMessages().size() : 0);
        long startTime = System.currentTimeMillis();
        try {
            // 避免修改调用方的 request 对象，创建副本设置 model 和 stream
            ChatCompletionRequest streamRequest = ChatCompletionRequest.builder()
                    .model(glmConfig.getModel())
                    .stream(true)
                    .messages(request.getMessages())
                    .tools(request.getTools())
                    .toolChoice(request.getToolChoice())
                    .build();

            String body = objectMapper.writeValueAsString(streamRequest);
            log.debug("[GLM API] 请求体大小: {} bytes", body.length());
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(glmConfig.getBaseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + glmConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());

            log.info("[GLM API] 收到响应, statusCode={}, 耗时={}ms",
                    response.statusCode(), System.currentTimeMillis() - startTime);

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[GLM API] 流式调用失败, statusCode={}, error={}", response.statusCode(), errorBody);
                throw new RuntimeException("GLM API error " + response.statusCode() + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                int chunkCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            log.info("[GLM API] 流式响应结束, 共接收 {} 个chunk, 总耗时={}ms",
                                    chunkCount, System.currentTimeMillis() - startTime);
                            break;
                        }
                        ChatCompletionChunk chunk = objectMapper.readValue(data, ChatCompletionChunk.class);
                        onChunk.accept(chunk);
                        chunkCount++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("[GLM API] 流式调用异常, 耗时={}ms", System.currentTimeMillis() - startTime, e);
            throw new RuntimeException("调用 GLM API 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文本嵌入向量（用于 RAG 语义检索）
     */
    public List<Double> getEmbedding(String text) {
        log.info("[GLM API] 开始调用 embeddings, text长度={}", text != null ? text.length() : 0);
        long startTime = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(
                    java.util.Map.of("model", glmConfig.getEmbeddingModel(), "input", text));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(glmConfig.getBaseUrl() + "/embeddings"))
                    .header("Authorization", "Bearer " + glmConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            log.info("[GLM API] embeddings 响应, statusCode={}, 耗时={}ms",
                    response.statusCode(), System.currentTimeMillis() - startTime);

            if (response.statusCode() != 200) {
                log.error("[GLM API] embeddings 调用失败, statusCode={}, error={}",
                        response.statusCode(), response.body());
                throw new RuntimeException("Embedding API error: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");
            List<Double> result = objectMapper.convertValue(embeddingNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
            log.info("[GLM API] embeddings 成功, 向量维度={}", result.size());
            return result;
        } catch (Exception e) {
            log.warn("[GLM API] embeddings 调用失败, 将降级为关键词搜索, 耗时={}ms, error={}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            return null;
        }
    }
}
