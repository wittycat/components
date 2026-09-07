package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 示例 2:状态合并策略(Channel)与并行分支(fan-out / fan-in)。
 *
 * 运行方式:
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example2_StateAndChannels
 *
 * 【知识点】
 * 1. 图的拓扑:splitter 一个节点"扇出"两条并行分支(worker1 / worker2),两条分支再"扇入"到 joiner。
 *      START → splitter → (worker1、worker2 并行) → joiner → END
 *    LangGraph4j 支持从一个节点 addEdge 到多个目标(并行),joiner 会等两条分支都执行完才运行——
 *    这就是所谓 fan-out / fan-in,是 Map-Reduce 式任务编排的基础。
 * 2. 状态 schema:构造 StateGraph 时除了状态工厂,还可以传入 Map<String, Channel<?>> 声明
 *    "每个 key 用什么策略合并状态更新":
 *      - 不在 schema 里声明的 key:默认"后写覆盖前写"(适合单个归属明确的值);
 *      - Channels.appender(...):把节点返回的 List "追加"进已有 List(适合消息列表、日志这类只增不改的数据);
 *      - Channels.base(reducer):完全自定义合并逻辑(把上一个值和新值怎么合成一个值写出来)。
 * 3. 本例 worker1、worker2 都往 logs(appender 通道)写一条,joiner 读到的 logs
 *    一定是两条分支结果的并集——并行分支不会互相覆盖,这就是 Channel 存在的意义。
 */
public class Example2_StateAndChannels {

    static class MyState extends AgentState {
        MyState(Map<String, Object> data) {
            super(data);
        }

        List<String> logs() {
            return value("logs", List.of());
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. 声明状态 schema:logs 用 appender 通道(追加语义)
        Map<String, Channel<?>> schema = Map.of(
                "logs", Channels.appender(ArrayList::new));

        var workflow = new StateGraph<>(schema, MyState::new)
                .addNode("splitter", AsyncNodeAction.node_async(state -> {
                    System.out.println("[splitter] 分发任务");
                    return Map.of("logs", List.of("splitter: 分发了任务"));
                }))
                .addNode("worker1", AsyncNodeAction.node_async(state ->
                        Map.of("logs", List.of("worker1: 完成数据清洗"))))
                .addNode("worker2", AsyncNodeAction.node_async(state ->
                        Map.of("logs", List.of("worker2: 完成特征提取"))))
                .addNode("joiner", AsyncNodeAction.node_async(state -> {
                    System.out.println("[joiner] 收到的汇总日志 = " + state.logs());
                    return Map.of("logs", List.of("joiner: 汇总完成"));
                }))
                // 2. 扇出:splitter 同时指向 worker1 和 worker2,两者并行执行
                .addEdge(StateGraph.START, "splitter")
                .addEdge("splitter", "worker1")
                .addEdge("splitter", "worker2")
                // 3. 扇入:两条分支都汇入 joiner,joiner 等所有分支结束后才执行
                .addEdge("worker1", "joiner")
                .addEdge("worker2", "joiner")
                .addEdge("joiner", StateGraph.END);

        var graph = workflow.compile();

        var finalState = graph.invoke(Map.of()).orElseThrow();
        System.out.println("\n最终 logs(注意 splitter/worker/joiner 的记录全部被追加保留):");
        finalState.logs().forEach(log -> System.out.println("  - " + log));
    }
}
