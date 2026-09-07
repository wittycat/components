package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 示例 7:子图(Subgraph)—— 把一张图当作另一个图的节点,实现分层/模块化编排。
 *
 * 运行方式:
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example7_Subgraph
 *
 * 【知识点】
 * 1. addNode("名字", 某张 StateGraph):子图直接作为父图的一个节点。
 *    执行到这个节点时,先跑完子图(内部按子图自己的边走),再回到父图继续。
 *    本例父图: START → child(子图: plan → execute) → report → END
 * 2. 什么时候需要子图?
 *      - 一段流程在多个图里复用(比如"标准检索流程"被几个业务图共享);
 *      - 团队分工:不同人维护不同子图,最终拼装;
 *      - 多 Agent 系统:每个 Agent 是一张独立小图,由主管图调度(示例 10 的前置知识)。
 * 3. 状态共享:子图和父图用同一种 State 类型时,状态"透明共享",子图节点对状态的修改
 *    父图直接可见(本例 logs 是共享的 appender 通道)。
 *    如果子图想用自己的私有 State,就要做"父状态 → 子状态 → 父状态"的字段映射,
 *    进阶可参考官方 how-tos 里的 subgraph 映射写法,本例先掌握同构共享。
 */
public class Example7_Subgraph {

    static class TaskState extends AgentState {
        TaskState(Map<String, Object> data) {
            super(data);
        }

        List<String> logs() {
            return value("logs", List.of());
        }
    }

    public static void main(String[] args) throws Exception {
        var schema = Map.<String, Channel<?>>of("logs", Channels.appender(ArrayList::new));

        // 1. 先定义子图:一张独立、完整的小图(可以单独测试、单独复用)
        var childWorkflow = new StateGraph<>(schema, TaskState::new)
                .addNode("plan", AsyncNodeAction.node_async(state ->
                        Map.of("logs", List.of("child.plan: 拆解任务为 3 个子步骤"))))
                .addNode("execute", AsyncNodeAction.node_async(state ->
                        Map.of("logs", List.of("child.execute: 执行完毕"))))
                .addEdge(StateGraph.START, "plan")
                .addEdge("plan", "execute")
                .addEdge("execute", StateGraph.END);

        // 2. 父图把子图当节点用
        var parentWorkflow = new StateGraph<>(schema, TaskState::new)
                .addNode("child", childWorkflow) // 就是这一行
                .addNode("report", AsyncNodeAction.node_async(state -> {
                    System.out.println("[report] 子图日志已被父图看到 = " + state.logs().size() + " 条");
                    return Map.of("logs", List.of("parent.report: 出报告"));
                }))
                .addEdge(StateGraph.START, "child")
                .addEdge("child", "report")
                .addEdge("report", StateGraph.END);

        var graph = parentWorkflow.compile();

        System.out.println("stream 执行轨迹(注意 child 内部的 plan/execute 也会依次出现):");
        for (NodeOutput<TaskState> out : graph.stream(Map.of())) {
            System.out.printf("  -> 节点[%s] logs=%s%n", out.node(), out.state().logs());
        }
    }
}
