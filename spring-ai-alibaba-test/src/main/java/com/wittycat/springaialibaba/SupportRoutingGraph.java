package com.wittycat.springaialibaba;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;

/**
 * "客服意图路由"工作流图 —— 示例 4(CLI)和示例 6(Web SSE)共用。
 *
 * 流程:START → classify(LLM 结构化输出意图) → 条件边分流 → tech/billing/other 应答节点 → END
 *
 * 设计要点(与示例 3 的差异):节点里调 ChatClient,这就是"LLM 节点"的全部秘密;
 * classify 用 .entity(Category.class) 让模型只能返回枚举值,条件边拿它路由,不会出现非法分支。
 */
public final class SupportRoutingGraph {

    /** 意图枚举:LLM 结构化输出的目标类型,也是条件边的路由 key */
    public enum Category { TECH, BILLING, OTHER }

    /** 状态 key:question 输入 / category 分类 / answer 应答 / steps 执行轨迹 */
    public static final String QUESTION = "question";
    public static final String CATEGORY = "category";
    public static final String ANSWER = "answer";
    public static final String STEPS = "steps";

    private SupportRoutingGraph() {
    }

    public static StateGraph build(ChatClient chatClient) {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(QUESTION, new ReplaceStrategy());
            strategies.put(CATEGORY, new ReplaceStrategy());
            strategies.put(ANSWER, new ReplaceStrategy());
            strategies.put(STEPS, new AppendStrategy());
            return strategies;
        };

        try {
            return new StateGraph("support-routing", keyStrategyFactory)
                    // 意图分类节点:LLM 只回一个枚举词
                    .addNode("classify", AsyncNodeAction.node_async(state -> {
                        Category category = chatClient.prompt()
                                .system("你是客服路由器。把用户问题分类为:TECH(技术故障/功能异常)、"
                                        + "BILLING(计费/退款/账单)、OTHER(其他)。只输出一个分类词。")
                                .user(state.value(QUESTION, ""))
                                .call()
                                .entity(Category.class);
                        System.out.println(">> [classify 节点] 意图 = " + category);
                        return Map.of(CATEGORY, category, STEPS, "classify:" + category);
                    }))
                    // 三个专职应答节点:各自的系统提示词写死职责,这比一个大而全的提示词更可控
                    .addNode("tech_answer", AsyncNodeAction.node_async(state ->
                            answerNode(chatClient, "资深技术支持工程师", "给出 3 步以内的排查步骤,每步一句话。", state)))
                    .addNode("billing_answer", AsyncNodeAction.node_async(state ->
                            answerNode(chatClient, "专精计费的客服", "说明退款/账单的处理路径和时效,态度诚恳。", state)))
                    .addNode("other_answer", AsyncNodeAction.node_async(state ->
                            answerNode(chatClient, "友好的通用客服", "简洁回答,并说明可转人工。", state)))
                    .addEdge(StateGraph.START, "classify")
                    // 条件边:分类结果是 TECH/BILLING/OTHER 分别进哪个节点
                    .addConditionalEdges("classify",
                            AsyncEdgeAction.edge_async(state -> state.value(CATEGORY, Category.OTHER).name()),
                            Map.of(Category.TECH.name(), "tech_answer",
                                    Category.BILLING.name(), "billing_answer",
                                    Category.OTHER.name(), "other_answer"))
                    .addEdge("tech_answer", StateGraph.END)
                    .addEdge("billing_answer", StateGraph.END)
                    .addEdge("other_answer", StateGraph.END);
        } catch (Exception e) {
            // addNode/addEdge 声明的是受检异常 GraphStateException,静态工厂里转成运行时抛出
            throw new IllegalStateException("构建 support-routing 图失败", e);
        }
    }

    /** 应答节点通用实现:专属 system 提示词 + 用户问题,生成结果写回 answer */
    private static Map<String, Object> answerNode(ChatClient chatClient, String role, String style,
                                                  OverAllState state) {
        String question = state.value(QUESTION, "");
        String answer = chatClient.prompt()
                .system("你是" + role + "。" + style + "回答控制在 120 字以内。")
                .user(question)
                .call()
                .content();
        return Map.of(ANSWER, answer, STEPS, "answer:" + answer.substring(0, Math.min(20, answer.length())) + "...");
    }
}
