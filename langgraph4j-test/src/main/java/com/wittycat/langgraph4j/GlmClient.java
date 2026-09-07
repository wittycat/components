package com.wittycat.langgraph4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 一个极简的 GLM 客户端(OpenAI 兼容 /chat/completions 协议),LLM 示例(8~10)共用。
 *
 * 【知识点】
 * 1. 请求体就是普通的 OpenAI Chat 格式 JSON:
 *      { "model": "...", "messages": [ {"role":"user","content":"..."}, ... ], "tools": [...] }
 *    messages 的角色:system(指令)/ user(用户)/ assistant(模型回复)/ tool(工具执行结果,
 *    必须带 tool_call_id 对应某次工具调用)。
 * 2. 两个入口:
 *      - chat(system, user):最简单的"一问一答",示例 8、10 用;
 *      - chat(messages, tools):完整控制消息列表 + 工具定义,返回 assistant 原始消息
 *        (可能带 tool_calls 字段),示例 9 的 ReAct 循环靠它。
 * 3. 工具定义(tools)也是 OpenAI 的 JSON Schema 格式:
 *      { "type":"function", "function": { "name", "description", "parameters": {...JSON Schema...} } }
 *    模型并不执行工具,它只决定"要不要调、调哪个、参数是什么"(tool_calls),
 *    真正执行是我们在代码里做的,再把结果以 role=tool 消息喂回去——这就是示例 9 要演示的循环。
 */
public final class GlmClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GlmClient() {
        this.apiKey = GlmConfig.resolveApiKey();
        this.baseUrl = GlmConfig.baseUrl();
        this.model = GlmConfig.model();
    }

    /** 最简单的对话:system 提示词 + user 提问,返回 assistant 的文本回复 */
    public String chat(String system, String user) {
        var message = chat(
                List.of(msg("system", system), msg("user", user)),
                null);
        Object content = message.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    /**
     * 完整控制入口:传入任意消息列表(可含 assistant/tool 消息)和可选的工具定义,
     * 返回 assistant 的原始消息 Map(role/content,可能还有 tool_calls)。
     *
     * @param tools OpenAI function 格式的工具定义,不需要工具时传 null
     */
    public Map<String, Object> chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools) {
        try {
            var body = mapper.createObjectNode();
            body.put("model", model);
            body.set("messages", mapper.valueToTree(messages));
            body.put("stream", false);
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", mapper.valueToTree(tools));
            }

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GLM 调用失败 HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            // 响应结构: { choices: [ { message: {...} } ], usage: {...} }
            var root = mapper.readTree(response.body());
            return mapper.convertValue(root.path("choices").path(0).path("message"),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("GLM 调用异常: " + e.getMessage(), e);
        }
    }

    // ── 消息构造辅助方法:让示例代码里拼消息列表更可读 ────────────────────

    /** {"role": ..., "content": ...} */
    public static Map<String, Object> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    /** 工具结果消息:tool_call_id 必须对应 assistant 消息里某次 tool_calls 的 id */
    public static Map<String, Object> toolResult(String toolCallId, String content) {
        return Map.of("role", "tool", "tool_call_id", toolCallId, "content", content);
    }
}
