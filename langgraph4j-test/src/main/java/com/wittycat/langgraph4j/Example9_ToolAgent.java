package com.wittycat.langgraph4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 示例 9:工具调用 Agent —— 手写一个 ReAct 循环(学懂本例,你就理解了 Agent 的本质)。
 *
 * 运行方式(需要先在 application-local.yml 配好 glm.api-key):
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example9_ToolAgent
 *
 * 【知识点】
 * 1. 图结构就是一个环:
 *        ┌──────────────────────────────┐
 *        ▼                              │
 *      agent ──条件边──> 有工具调用? ──是──> tools 节点
 *        │                    │
 *        否(直接回答)          └─> 执行工具,结果回填消息列表,回到 agent
 *        ▼
 *       END
 *    "agent 会自己思考并使用工具"并不是魔法,而是:LLM 返回 tool_calls → 我们执行工具 →
 *    把结果喂回去 → LLM 继续思考,直到它不再要求调工具。
 * 2. 消息列表 messages 用 appender 通道:user / assistant / tool 三种消息不断追加,
 *    每次进 agent 节点都把完整历史发给 GLM——这就是 Agent 的"短期记忆"。
 * 3. GLM(OpenAI 兼容协议)的工具调用流程:
 *      - 请求时带 tools(JSON Schema 描述每个工具的名字/用途/参数);
 *      - 模型想调工具时,assistant 消息里会带 tool_calls[{id, function:{name, arguments}}];
 *      - 我们执行工具,并以 {role:"tool", tool_call_id, content} 消息回填;
 *      - assistant 自己那条带 tool_calls 的消息也必须原样保留在消息列表里(配对要求)。
 * 4. recursionLimit:环图必须有兜底,RunnableConfig.builder().recursionLimit(n)
 *    限制最多执行多少步,防止模型反复调工具停不下来。
 */
public class Example9_ToolAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static class AgentChatState extends AgentState {
        AgentChatState(Map<String, Object> data) {
            super(data);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages() {
            return value("messages", List.of());
        }
    }

    public static void main(String[] args) throws Exception {
        var glm = new GlmClient();

        var workflow = new StateGraph<>(
                        Map.<String, Channel<?>>of("messages", Channels.appender(ArrayList::new)),
                        AgentChatState::new)
                .addNode("agent", AsyncNodeAction.node_async(state -> {
                    System.out.println("[agent] 带着 " + state.messages().size() + " 条历史消息询问 GLM……");
                    var assistantMessage = glm.chat(state.messages(), toolDefinitions());
                    System.out.println("[agent] GLM 回复:" + summarize(assistantMessage));
                    // assistant 消息必须原样入列(可能带 tool_calls,和后面的 tool 结果配对)
                    return Map.of("messages", List.of(assistantMessage));
                }))
                .addNode("tools", AsyncNodeAction.node_async(state -> {
                    // 最后一条消息一定是带 tool_calls 的 assistant 消息
                    var last = state.messages().get(state.messages().size() - 1);
                    var results = new ArrayList<Map<String, Object>>();
                    for (var call : toolCalls(last)) {
                        String name = String.valueOf(((Map<?, ?>) call.get("function")).get("name"));
                        String arguments = String.valueOf(((Map<?, ?>) call.get("function")).get("arguments"));
                        String callId = String.valueOf(call.get("id"));
                        System.out.println("[tools] 执行 " + name + "(" + arguments + ")");
                        results.add(GlmClient.toolResult(callId, executeTool(name, arguments)));
                    }
                    return Map.of("messages", results);
                }))
                .addEdge(StateGraph.START, "agent")
                .addConditionalEdges("agent",
                        AsyncEdgeAction.edge_async(state -> {
                            var last = state.messages().get(state.messages().size() - 1);
                            return toolCalls(last).isEmpty() ? "final" : "continue";
                        }),
                        Map.of("continue", "tools", "final", StateGraph.END))
                .addEdge("tools", "agent");

        var graph = workflow.compile();

        String question = "查一下北京和上海现在的天气,然后告诉我哪个城市更适合户外跑步,为什么?";
        System.out.println(">>> 用户:" + question + "\n");

        var config = RunnableConfig.builder().recursionLimit(25).build();
        var finalState = graph.invoke(Map.of(
                "messages", List.of(GlmClient.msg("user", question))), config).orElseThrow();

        // 最后一条 assistant 消息就是最终回答
        var last = finalState.messages().get(finalState.messages().size() - 1);
        System.out.println("\n==================== 最终回答 ====================");
        System.out.println(last.get("content"));
    }

    // ── 工具定义(JSON Schema)与模拟实现 ──────────────────────────────

    private static List<Map<String, Object>> toolDefinitions() {
        var cityParam = Map.of("type", "object",
                "properties", Map.of("city", Map.of("type", "string", "description", "城市名,如:北京")),
                "required", List.of("city"));
        return List.of(
                Map.of("type", "function", "function", Map.of(
                        "name", "queryWeather",
                        "description", "查询指定城市的当前天气",
                        "parameters", cityParam)),
                Map.of("type", "function", "function", Map.of(
                        "name", "queryPrice",
                        "description", "查询商品价格",
                        "parameters", Map.of("type", "object",
                                "properties", Map.of("product", Map.of("type", "string", "description", "商品名")),
                                "required", List.of("product")))));
    }

    /** 模拟工具执行:真实项目里这里可能是一次 HTTP/RPC/数据库调用 */
    private static String executeTool(String name, String arguments) {
        try {
            Map<?, ?> params = MAPPER.readValue(arguments, Map.class);
            return switch (name) {
                case "queryWeather" -> switch (String.valueOf(params.get("city"))) {
                    case "北京" -> "晴,气温 26℃,湿度 40%,空气质量优,微风";
                    case "上海" -> "小雨,气温 22℃,湿度 85%,空气质量良,东北风 3 级";
                    default -> "多云,24℃";
                };
                case "queryPrice" -> String.valueOf(params.get("product")) + " 的价格是 999 元(模拟数据)";
                default -> "未知工具: " + name;
            };
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    // ── 小工具方法 ───────────────────────────────────────────────────

    /** 从 assistant 消息里取 tool_calls(没有则返回空列表) */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toolCalls(Map<String, Object> assistantMessage) {
        Object calls = assistantMessage.get("tool_calls");
        return calls == null ? List.of() : (List<Map<String, Object>>) calls;
    }

    /** 控制台打印用的摘要:有工具调用就列出工具名,否则截取文本回复 */
    private static String summarize(Map<String, Object> assistantMessage) {
        var calls = toolCalls(assistantMessage);
        if (!calls.isEmpty()) {
            return "要求调用工具 " + calls.stream()
                    .map(c -> String.valueOf(((Map<?, ?>) c.get("function")).get("name")))
                    .toList();
        }
        String content = String.valueOf(assistantMessage.get("content"));
        return content.length() > 60 ? content.substring(0, 60) + "……" : content;
    }
}
