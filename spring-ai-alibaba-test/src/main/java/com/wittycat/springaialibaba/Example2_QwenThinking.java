package com.wittycat.springaialibaba;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 示例 2:Qwen 思考模式 —— DashScopeChatOptions 的厂商原生参数,这是"原生协议接入"相对
 * "OpenAI 兼容直连"最直观的收益(spring-ai-test 里做不到,只能拿到最终答案)。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springaialibaba.Example2_QwenThinking
 *
 * 【知识点】
 * 1. DashScopeChatOptions 里有一批 OpenAI 协议没有的参数:enableThinking/thinkingBudget(思考模式)、
 *    enableSearch(联网搜索)、repetitionPenalty 等。enableThinking=true 时 Qwen 会先"内心独白"再作答,
 *    深度推理类问题(数学/逻辑/代码)准确率明显更高,代价是时延和 token 消耗。
 * 2. 思考内容不在正文的 content 里,而是通过响应元数据 reasoningContent 透出:
 *    assistantMessage.getMetadata().get("reasoningContent")。OpenAI 兼容端点不会给你这个字段。
 * 3. 运行时参数用 .options(...) 传入,和 defaultOptions 合并(本例 model 沿用默认的 qwen-plus)。
 *    注意它是 DashScope 专属类型 —— 用了它,这段代码就绑定了这个模型实现,这是"用厂商原生能力"的固有代价。
 * 4. 模型支持情况:qwen3 系列/qwen-plus 支持思考开关;老模型(如 qwen-turbo 早期版本)会直接报错,
 *    报错时把 dashscope.model 换成 qwen-plus 或 qwen3 系列再试。
 */
public class Example2_QwenThinking {

    public static void main(String[] args) {
        ChatClient chatClient = ChatClient.create(DashScopeModels.createChatModel());

        String question = "一个池塘里的荷叶每天翻倍,48 天长满整个池塘,问长满一半需要几天?";
        System.out.println("问题: " + question + "\n");

        System.out.println("========== 第 1 次:关思考(默认),直接作答 ==========");
        String direct = chatClient.prompt()
                .options(DashScopeChatOptions.builder().enableThinking(false).build())
                .user(question)
                .call()
                .content();
        System.out.println(direct + "\n");

        System.out.println("========== 第 2 次:开思考,先推理再作答 ==========");
        ChatResponse response = chatClient.prompt()
                .options(DashScopeChatOptions.builder()
                        .enableThinking(true)
                        // thinkingBudget 限制思考的 token 上限(仅部分 qwen3 系列模型支持,不支持的模型去掉这行)
                        // .thinkingBudget(1024)
                        .build())
                .user(question)
                .call()
                .chatResponse();

        AssistantMessage output = response.getResult().getOutput();

        // 思考内容在元数据里,正文 getText() 只有最终答案
        Object reasoning = output.getMetadata().get("reasoningContent");
        System.out.println("[思考过程 reasoningContent]\n" + (reasoning == null ? "(无,确认模型是否支持思考模式)" : reasoning) + "\n");

        System.out.println("[最终答案]\n" + output.getText());
    }
}
