package com.wittycat.langgraph4j;

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
 * 示例 10:多智能体(Multi-Agent)监督者模式 —— 用你已经学过的图原语搭一个"AI 团队"。
 *
 * 运行方式(需要先在 application-local.yml 配好 glm.api-key):
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example10_MultiAgent
 *
 * 【知识点】
 * 1. 多智能体没有新的框架 API,就是"图 + LLM 路由"的组合:
 *        supervisor(主管,LLM 决定下一步派谁)
 *          ├── RESEARCHER → researcher(研究员节点)──┐
 *          ├── WRITER     → writer(写手节点)  ──────┼→ 回到 supervisor
 *          └── FINISH → END                          ┘
 *    这就是经典的 Supervisor 模式:主管不干活,只看对话历史决定"派谁上场 / 是否收工"。
 * 2. 两个专家节点各自是一个"LLM + 角色提示词",干完活把发言追加进共享的 messages
 *    (示例 4 的 appender 通道记忆 + 示例 9 的消息列表,在这里合流)。
 * 3. supervisor 的输出用最朴素的约束拿到结构化结果:提示词要求"只输出 RESEARCHER/WRITER/FINISH
 *    之一",再用条件边把字符串路由到对应节点——这就是示例 3 的条件边在多智能体里的用法。
 *    生产上更稳的做法是让它通过 function calling 输出(见示例 9),或输出 JSON 后解析。
 * 4. 防失控三件套:recursionLimit 兜底 + 状态里记轮数 + 提示词里写明"任务完成就 FINISH"。
 */
public class Example10_MultiAgent {

    static class TeamState extends AgentState {
        TeamState(Map<String, Object> data) {
            super(data);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages() {
            return value("messages", List.of());
        }

        String next() {
            return value("next", "");
        }
    }

    public static void main(String[] args) throws Exception {
        var glm = new GlmClient();
        var task = "写一段 150 字左右的短文,介绍北京的秋天";

        // ── 三个"角色"节点(显式标注 TeamState,帮助编译器推断泛型)──────────
        var supervisor = AsyncNodeAction.node_async((TeamState state) -> {
            String decision = glm.chat("""
                            你是团队主管,管理两名专家:RESEARCHER(负责核实素材、补充事实)和 WRITER(负责成文)。
                            根据对话历史判断下一步:素材不够就输出 RESEARCHER;素材够了但还没成文就输出 WRITER;
                            任务已完成就输出 FINISH。你只能输出 RESEARCHER、WRITER、FINISH 三个词之一,不要输出任何其他内容。
                            """,
                    dialogueOf(state));
            decision = normalize(decision);
            System.out.println("[supervisor] 决定下一步 → " + decision);
            return Map.of("next", decision);
        });

        var researcher = AsyncNodeAction.node_async((TeamState state) -> {
            System.out.println("[researcher] 收集素材中……");
            String reply = glm.chat(
                    "你是研究员,负责为写作任务补充事实素材(如北京秋天的气候特点、香山红叶、银杏等),用 2~3 条要点回答。",
                    dialogueOf(state));
            System.out.println("[researcher] " + brief(reply));
            return Map.of("messages", List.of(GlmClient.msg("assistant", "【研究员】" + reply)));
        });

        var writer = AsyncNodeAction.node_async((TeamState state) -> {
            System.out.println("[writer] 根据素材成文中……");
            String reply = glm.chat(
                    "你是写手,根据对话中的素材和要求写短文,直接输出正文,150 字左右。",
                    dialogueOf(state));
            System.out.println("[writer] " + brief(reply));
            return Map.of("messages", List.of(GlmClient.msg("assistant", "【写手】" + reply)));
        });

        // ── 组装团队图 ────────────────────────────────────────────────
        var schema = Map.<String, Channel<?>>of("messages", Channels.appender(ArrayList::new));
        var workflow = new StateGraph<>(schema, TeamState::new)
                .addNode("supervisor", supervisor)
                .addNode("researcher", researcher)
                .addNode("writer", writer)
                .addEdge(StateGraph.START, "supervisor")
                // 主管的条件路由:示例 3 的条件边 + 示例 9 的回环,组合即多智能体
                .addConditionalEdges("supervisor",
                        AsyncEdgeAction.edge_async((TeamState state) -> state.next()),
                        Map.of("RESEARCHER", "researcher", "WRITER", "writer", "FINISH", StateGraph.END))
                .addEdge("researcher", "supervisor")
                .addEdge("writer", "supervisor");

        var graph = workflow.compile();

        System.out.println(">>> 任务:" + task + "\n");
        var config = RunnableConfig.builder().recursionLimit(30).build();
        var finalState = graph.invoke(Map.of(
                "messages", List.of(GlmClient.msg("user", task))), config).orElseThrow();

        // 最后一条消息通常就是成文(写手写完 → 主管判定 FINISH)
        var messages = finalState.messages();
        var last = messages.get(messages.size() - 1);
        System.out.println("\n==================== 最终成果 ====================");
        System.out.println(String.valueOf(last.get("content"))
                .replaceFirst("^【写手】", ""));
    }

    // ── 小工具方法 ───────────────────────────────────────────────────

    /** 把共享消息列表拼成一段对话文本,作为每个角色的输入 */
    private static String dialogueOf(TeamState state) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : state.messages()) {
            sb.append(m.get("content")).append('\n');
        }
        return sb.toString();
    }

    /** GLM 偶尔会多输出引号或前缀,这里清洗成三个合法路由之一,不认识就强制收工 */
    private static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        for (String allowed : List.of("RESEARCHER", "WRITER", "FINISH")) {
            if (s.contains(allowed)) {
                return allowed;
            }
        }
        return "FINISH";
    }

    private static String brief(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "……" : oneLine;
    }
}
