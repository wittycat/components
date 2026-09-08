package com.wittycat.springaialibaba;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * 示例 3:StateGraph 纯状态机(不调 LLM)—— Spring AI Alibaba 最核心的独有能力:
 * Graph 编排(对标 Python LangGraph)。本例先剥离模型,只看"图"本身怎么写。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springaialibaba.Example3_StateGraph
 *
 * 【知识点】
 * 1. 为什么需要 Graph?ChatClient 是"一问一答",多步工作流(先校验→再分类→按分支走不同处理→汇总)
 *    用 if/else 写在业务代码里会越来越乱。Graph 把"步骤(节点)"和"走向(边)"声明出来,框架负责调度,
 *    还能生成流程图、断点续跑、并行执行。
 * 2. 五个核心概念:
 *      - StateGraph:   图的定义(addNode/addEdge/addConditionalEdges),不可变描述;
 *      - OverAllState: 全局共享状态,节点返回的 Map 会按 KeyStrategy 合并进去,下一个节点可读;
 *      - NodeAction:   节点 = 一个 (state)->Map 的函数,返回的是"要写回状态的增量",不是全量;
 *      - EdgeAction:   条件边 = 一个 (state)->下一节点名 的函数,实现分支;
 *      - CompiledGraph:compile() 后的可执行图,invoke() 同步跑、stream() 按节点流式吐结果。
 * 3. KeyStrategy 决定同名字段怎么合并:ReplaceStrategy 后写覆盖(常规字段),
 *    AppendStrategy 追加成 List(本例的 steps 用来记录执行轨迹,跑完能看到图实际走了哪条路)。
 * 4. addNode/addEdge 都是普通 Java 调用,graph 完全不依赖 Spring —— 纯 main 就能跑(本例),
 *    Spring 里当 Bean 注入用也一样(见示例 6 的 Web 接口)。
 */
public class Example3_StateGraph {

    public static void main(String[] args) throws Exception {
        // ---- 1. 声明状态的合并策略:哪些 key 进状态、各用什么策略合并 ----
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("raw", new ReplaceStrategy());       // 原始输入(只写一次)
            strategies.put("clean", new ReplaceStrategy());     // 清洗后的文本
            strategies.put("topic", new ReplaceStrategy());     // 分类结果
            strategies.put("report", new ReplaceStrategy());    // 最终报告
            strategies.put("steps", new AppendStrategy());      // 执行轨迹:每个节点追加一笔
            return strategies;
        };

        // ---- 2. 定义图:节点 + 边 + 条件边 ----
        StateGraph graph = new StateGraph("feedback-pipeline", keyStrategyFactory)
                // 节点 1:文本清洗(普通 Java 函数,不涉及模型)
                .addNode("normalize", AsyncNodeAction.node_async(Example3_StateGraph::normalize))
                // 节点 2:关键词分类(故意用最土的关键词匹配,证明图和"谁来做这件事"解耦)
                .addNode("classify", AsyncNodeAction.node_async(Example3_StateGraph::classify))
                // 节点 3:组装报告
                .addNode("format", AsyncNodeAction.node_async(Example3_StateGraph::format))
                // 入口边:START -> normalize
                .addEdge(StateGraph.START, "normalize")
                // 条件边:normalize 之后看清洗结果,空输入直接结束,否则进 classify
                .addConditionalEdges("normalize",
                        AsyncEdgeAction.edge_async(state ->
                                state.value("clean", "").isBlank() ? "reject" : "continue"),
                        Map.of("continue", "classify", "reject", StateGraph.END))
                // 普通边:classify -> format -> END
                .addEdge("classify", "format")
                .addEdge("format", StateGraph.END);

        // ---- 3. 编译并执行(顺带打印 Mermaid 流程图,粘到 mermaid.live 可视化) ----
        System.out.println("=== 图结构(Mermaid,粘到 https://mermaid.live 查看) ===");
        System.out.println(graph.getGraph(GraphRepresentation.Type.MERMAID, "feedback-pipeline").content() + "\n");

        OverAllState result = graph.compile()
                .invoke(Map.of("raw", "   Spring AI Alibaba   的 Graph   编排真好用,   少写了好多 if/else   "))
                .orElseThrow();

        System.out.println("=== 执行结果 ===");
        System.out.println("清洗后: " + result.value("clean").orElse(""));
        System.out.println("分类:   " + result.value("topic").orElse(""));
        System.out.println("报告:   " + result.value("report").orElse(""));
        System.out.println("轨迹:   " + result.value("steps").orElseGet(java.util.List::of));

        // ---- 4. 再跑一次"空输入",看条件边走了另一条路(reject 直达 END,后面节点没执行) ----
        OverAllState rejected = graph.compile().invoke(Map.of("raw", "      ")).orElseThrow();
        System.out.println("\n=== 空输入时的轨迹(条件边直接把流程送到了 END) ===");
        System.out.println("轨迹:   " + rejected.value("steps").orElseGet(java.util.List::of));
        System.out.println("报告:   " + rejected.value("report").orElse("(未生成 —— format 节点根本没跑)"));
    }

    /** 节点:清洗文本。返回的 Map 是"要合并进状态的增量" */
    static Map<String, Object> normalize(OverAllState state) {
        String clean = state.value("raw", "").trim().replaceAll("\\s+", " ");
        return Map.of("clean", clean, "steps", "normalize:清洗完成");
    }

    /** 节点:关键词分类 */
    static Map<String, Object> classify(OverAllState state) {
        String clean = state.value("clean", "");
        String topic = clean.contains("Graph") || clean.contains("AI") ? "技术反馈" : "一般反馈";
        return Map.of("topic", topic, "steps", "classify:" + topic);
    }

    /** 节点:组装最终报告 */
    static Map<String, Object> format(OverAllState state) {
        String report = "[" + state.value("topic", "?") + "] " + state.value("clean", "");
        return Map.of("report", report, "steps", "format:报告已生成");
    }
}
