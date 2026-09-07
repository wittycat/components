package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * 示例 6:Human-in-the-loop —— 图执行到关键节点前暂停,等人(或审批系统)介入后再继续。
 *
 * 运行方式:
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example6_HumanInTheLoop
 *
 * 【知识点】
 * 1. 典型场景:AI 生成的文章/邮件/订单操作,先暂停让人审核,通过才发布/执行。
 *    图结构:  draft(写稿) → review(审阅) → 发布或改稿
 * 2. 三个关键 API:
 *      - CompileConfig.builder().interruptBefore("review"):编译时声明"执行到 review 前暂停"
 *        (必须配合 CheckpointSaver,因为暂停点要靠 checkpoint 记住);
 *      - graph.getState(config):暂停后查看当前快照,next() 就是"接下来要执行的节点";
 *      - graph.updateState(config, 人工补充的数据):把人的输入注入状态,
 *        再 invoke(GraphInput.resume(), config) 从暂停点恢复执行。
 * 3. 中断的"发生"和"恢复"是两次独立的调用,之间可以隔几小时甚至几天(状态存在 saver 里)。
 *    本例用 Console 输入模拟"人工",实际生产里这两次调用往往来自两个 HTTP 请求。
 * 4. review 后面的条件边演示了"驳回"路径:改稿后再次进入 review,会再次中断等待人工——
 *    HITL 循环审批就是这么搭出来的。
 */
public class Example6_HumanInTheLoop {

    static class DocState extends AgentState {
        DocState(Map<String, Object> data) {
            super(data);
        }

        String draft() {
            return value("draft", "");
        }

        Boolean approved() {
            return value("approved", false);
        }
    }

    public static void main(String[] args) throws Exception {
        var workflow = new StateGraph<>(DocState::new)
                .addNode("draft", AsyncNodeAction.node_async(state ->
                        Map.of("draft", "《" + state.value("topic", "无题") + "》的初稿内容……")))
                .addNode("review", AsyncNodeAction.node_async(state -> {
                    System.out.println("[review] 人工意见:approved=" + state.approved());
                    return Map.of();
                }))
                .addNode("revise", AsyncNodeAction.node_async(state ->
                        Map.of("draft", state.draft() + "(按意见修改后的第二稿)")))
                .addNode("publish", AsyncNodeAction.node_async(state -> {
                    System.out.println("[publish] 已发布:" + state.draft());
                    return Map.of();
                }))
                .addEdge(StateGraph.START, "draft")
                .addEdge("draft", "review")
                // 条件边:通过 → 发布;驳回 → 改稿后回到 review 再审
                .addConditionalEdges("review",
                        AsyncEdgeAction.edge_async(state -> state.approved() ? "通过" : "驳回"),
                        Map.of("通过", "publish", "驳回", "revise"))
                .addEdge("revise", "review")
                .addEdge("publish", StateGraph.END);

        // 关键点 1:挂 Checkpoint + 声明在 review 前中断
        var graph = workflow.compile(CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .interruptBefore("review")
                .build());

        var config = RunnableConfig.builder().threadId("doc-42").build();

        // ── 第一次运行:跑完 draft 后在 review 前停住 ─────────────────────
        System.out.println(">>> 第 1 次调用:写稿,然后暂停等待人工审阅");
        graph.invoke(GraphInput.args(Map.of("topic", "LangGraph4j 学习心得")), config);
        var snapshot = graph.getState(config);
        System.out.println("   已暂停,当前草稿:" + snapshot.state().draft());
        System.out.println("   下一个待执行节点:" + snapshot.next() + "(正是我们设定的中断点)");

        // ── 模拟人工审阅:第一次驳回 ───────────────────────────────────
        System.out.println("\n>>> 人工审阅(模拟):驳回,要求补充示例");
        graph.updateState(config, Map.of("approved", false));

        System.out.println(">>> 恢复执行:改稿 → 再次在 review 前中断");
        graph.invoke(GraphInput.resume(), config);
        System.out.println("   第二稿:" + graph.getState(config).state().draft());

        // ── 第二次人工审阅:通过 ──────────────────────────────────────
        System.out.println("\n>>> 人工审阅(模拟):通过");
        graph.updateState(config, Map.of("approved", true));
        graph.invoke(GraphInput.resume(), config);

        // ── 演示:如果没被中断,图会一气跑到 END ─────────────────────────
        System.out.println("\n>>> 对照组:换成不带 interrupt 的图,一次跑完(人工意见直接预置为通过)");
        var graphNoInterrupt = workflow.compile(); // 没有 saver、没有 interrupt
        for (NodeOutput<DocState> out : graphNoInterrupt.stream(
                Map.of("topic", "一次跑完", "approved", true))) {
            System.out.println("   -> " + out.node());
        }
    }
}
