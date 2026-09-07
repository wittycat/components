package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * 示例 8:把 LLM 调用封装成图节点 —— "大纲 → 写稿 → 润色"三节点写作流水线。
 *
 * 运行方式(需要先在 application-local.yml 配好 glm.api-key):
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example8_LlmNode
 *
 * 【知识点】
 * 1. LLM 节点的写法:和普通节点没有任何区别——读状态、调一次 GLM、返回对状态的增量更新。
 *    真正的工程价值在于:LLM 只是图里的一个普通步骤,重试、超时、人工审核(示例 6)、
 *    并行(示例 2)都可以用图的机制统一编排,而不是散落在业务代码里。
 * 2. 状态里每个中间产物(outline/draft/article)用默认的"覆盖"语义:
 *    每个节点只负责写自己的字段,天然就是流水线的"工序交接"。
 * 3. GlmClient 是手写的极简 OpenAI 兼容客户端(见该类注释),这里每个节点一次非流式调用。
 *    想做 token 级流式输出,可进阶研究 langgraph4j 的 StreamingOutput / StreamingChatGenerator。
 */
public class Example8_LlmNode {

    static class WriteState extends AgentState {
        WriteState(Map<String, Object> data) {
            super(data);
        }

        String topic() {
            return value("topic", "");
        }

        String outline() {
            return value("outline", "");
        }

        String draft() {
            return value("draft", "");
        }

        String article() {
            return value("article", "");
        }
    }

    public static void main(String[] args) throws Exception {
        var glm = new GlmClient();
        var topic = "为什么 Java 程序员值得学一套图编排框架";

        var workflow = new StateGraph<>(WriteState::new)
                // 节点 1:出大纲
                .addNode("outline", AsyncNodeAction.node_async(state -> {
                    System.out.println("[outline] 正在生成大纲……");
                    String outline = glm.chat(
                            "你是技术写作策划,只输出大纲,不要展开正文,控制在 5 行以内。",
                            "为文章《%s》列一个大纲".formatted(state.topic()));
                    System.out.println(outline + "\n");
                    return Map.of("outline", outline);
                }))
                // 节点 2:按大纲写初稿
                .addNode("draft", AsyncNodeAction.node_async(state -> {
                    System.out.println("[draft] 正在按大纲写初稿……");
                    String draft = glm.chat(
                            "你是技术作者,用中文写一篇 200 字左右的短文,直接输出正文。",
                            "按以下大纲写初稿:\n%s".formatted(state.outline()));
                    return Map.of("draft", draft);
                }))
                // 节点 3:润色定稿
                .addNode("polish", AsyncNodeAction.node_async(state -> {
                    System.out.println("[polish] 正在润色……");
                    String article = glm.chat(
                            "你是资深编辑,只做语言润色和结构微调,保持原意,直接输出润色后的全文。",
                            state.draft());
                    return Map.of("article", article);
                }))
                .addEdge(StateGraph.START, "outline")
                .addEdge("outline", "draft")
                .addEdge("draft", "polish")
                .addEdge("polish", StateGraph.END);

        var graph = workflow.compile();

        // 用 stream 观察"流水线"逐节点推进;最终文章从最后一个 NodeOutput 的状态里取
        String article = "";
        for (NodeOutput<WriteState> out : graph.stream(Map.of("topic", topic))) {
            System.out.println(">>> 节点[" + out.node() + "] 执行完成");
            if (!out.state().article().isEmpty()) {
                article = out.state().article();
            }
        }

        System.out.println("\n==================== 定稿 ====================");
        System.out.println(article);
    }
}
