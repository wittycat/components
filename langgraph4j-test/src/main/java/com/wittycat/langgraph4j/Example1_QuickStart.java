package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * 示例 1:最小可用的图 —— 状态、节点、边,以及两种运行方式。
 *
 * 运行方式(二选一):
 *   1. IDE 里直接运行 main 方法
 *   2. 命令行: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example1_QuickStart
 *
 * 【知识点】
 * 1. LangGraph4j 的核心思想:把 AI 应用的流程画成一张"有状态的有向图"。
 *      - State(状态): 数据在节点之间流转的唯一载体,本例是 MyState;
 *      - Node(节点): 接收当前状态,返回一个 Map 表示"对状态的部分更新",本例是 greeter / responder;
 *      - Edge(边): 决定节点执行完之后走哪里,START / END 是两个特殊端点。
 * 2. MyState 继承 AgentState,AgentState 本质是一个 Map<String,Object>,
 *    value(key, 默认值) 是类型安全的读取方式。子类用 getter 封装,业务代码就不用到处写字符串 key。
 * 3. 节点返回的 Map 是"增量更新"而不是全量替换:greeter 只写了 greeting,不影响 input。
 * 4. 两种运行方式:
 *      - invoke(): 同步执行完整张图,直接拿最终状态,适合拿结果;
 *      - stream(): 每执行完一个节点就吐出一个 NodeOutput(节点名 + 该时刻的状态),
 *        能清楚看到图的执行轨迹,这也是后续调试复杂图最常用的手段。
 * 5. addNode 的第二个参数用 AsyncNodeAction.node_async(...) 包装。
 *    LangGraph4j 内部全部用异步动作(CompletableFuture)驱动,同步写法只是给我们用的便利封装。
 */
public class Example1_QuickStart {

    /** 自定义状态:继承 AgentState,用 getter 封装对 Map 的类型安全读取 */
    static class MyState extends AgentState {
        MyState(Map<String, Object> data) {
            super(data);
        }

        String input() {
            return value("input", "");
        }

        String greeting() {
            return value("greeting", "");
        }

        String output() {
            return value("output", "");
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. 定义图:声明状态工厂(状态怎么构造),再往里加节点和边
        var workflow = new StateGraph<>(MyState::new)
                .addNode("greeter", AsyncNodeAction.node_async(state ->
                        Map.of("greeting", "你好," + state.input() + "!")))
                .addNode("responder", AsyncNodeAction.node_async(state ->
                        Map.of("output", state.greeting() + " 欢迎来到 LangGraph4j 的世界。")))
                .addEdge(StateGraph.START, "greeter")
                .addEdge("greeter", "responder")
                .addEdge("responder", StateGraph.END);

        // 2. 编译:StateGraph 只是"图纸",compile() 之后才是可以执行的 CompiledGraph
        CompiledGraph<MyState> graph = workflow.compile();

        // 3. 方式一:invoke —— 拿最终状态
        var finalState = graph.invoke(Map.of("input", "小明")).orElseThrow();
        System.out.println("invoke 最终 output = " + finalState.output());

        // 4. 方式二:stream —— 观察每个节点执行后的状态快照
        System.out.println("\nstream 执行轨迹:");
        for (NodeOutput<MyState> out : graph.stream(Map.of("input", "小红"))) {
            System.out.printf("  节点[%s] -> %s%n", out.node(), out.state().data());
        }
    }
}
