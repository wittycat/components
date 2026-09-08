package com.wittycat.springaialibaba;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;

/**
 * 示例 4:Graph + LLM 路由工作流 —— 图的节点里调用大模型,条件边按模型的判断结果分流。
 * 场景:客服入口把问题分成 技术故障/计费/其他 三类,各走专属应答节点。
 * 图的构建逻辑抽到 SupportRoutingGraph,Web 示例(示例 6)复用同一张图。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springaialibaba.Example4_GraphRouting
 *
 * 【知识点】
 * 1. "LLM 节点"没有任何魔法:就是在 NodeAction 里调 ChatClient,把结果写回状态。
 *    LLM 负责判断/生成,图负责调度 —— 两者正交组合。
 * 2. 意图分类用 .entity(枚举.class) 结构化输出(spring-ai-test 示例 6 学过的能力,原样搬过来):
 *    模型只会返回 TECH/BILLING/OTHER 之一,拿它驱动条件边,天然不会写出非法路由。
 * 3. 这种"先理解、再分流、各分支专职处理"的多步工作流,用 ChatClient + Advisor 很别扭
 *    (Advisor 是请求管线上的拦截器,表达不了"分支");用 Graph 声明出来则一目了然。
 *    这就是 Spring AI(管单次调用)与 Spring AI Alibaba Graph(管流程编排)的分工。
 * 4. RunnableConfig.threadId + MemorySaver 可以做"按会话持久化图中状态",本例不用,
 *    每次调用都是全新一遍;需要多轮的看示例 5 的 ReactAgent。
 */
public class Example4_GraphRouting {

    public static void main(String[] args) throws Exception {
        // 同一张图既给 CLI 用,也给 Web 示例用:Graph 是纯 Java 库,不绑定运行环境
        StateGraph graph = SupportRoutingGraph.build(
                org.springframework.ai.chat.client.ChatClient.create(DashScopeModels.createChatModel()));

        String question = "上个月的账单多扣了我 9.9 元会员费,怎么申请退款?";
        System.out.println("用户问题: " + question + "\n");

        // RunnableConfig.threadId 标识本次执行(多轮记忆/断点续跑都以它为 key)
        OverAllState result = graph.compile()
                .invoke(java.util.Map.of("question", question),
                        RunnableConfig.builder().threadId("demo").build())
                .orElseThrow();

        System.out.println("\n=== 执行结果 ===");
        System.out.println("意图分类: " + result.value("category").orElse("?"));
        System.out.println("执行轨迹: " + result.value("steps").orElseGet(java.util.List::of));
        System.out.println("\n专属应答(" + result.value("category").orElse("?") + " 节点生成):\n"
                + result.value("answer").orElse(""));
    }
}
