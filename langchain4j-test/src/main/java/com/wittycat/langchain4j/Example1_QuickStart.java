package com.wittycat.langchain4j;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 示例 1:最小可用 —— 创建模型、问一句话、拿到回答。
 *
 * 运行方式(二选一):
 *   1. IDE 里直接运行 main 方法
 *   2. 命令行: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example1_QuickStart
 *
 * 【知识点】
 * 1. LangChain4j 的定位:Java 版的 LangChain,把"接模型、消息、工具、记忆、RAG"
 *    都做成可组合的接口,和 agentscope-test("Agent 框架帮你搭好")、
 *    langgraph4j-test("图原语自己搭")是三种不同的抽象层级。
 * 2. ChatModel 是"对话模型"的统一抽象,任何厂商实现它,业务代码只面向接口。
 *    本例底层是 langchain4j-open-ai 的 OpenAiChatModel,但 baseUrl 指向智谱 GLM 的
 *    OpenAI 兼容端点(见 GlmModels)——换 DeepSeek/Kimi 只是改两个配置,代码零改动。
 * 3. chat(String) 是最便捷的重载:传用户问题字符串,直接拿回答文本。
 *    它内部帮你做了三件事:包成 UserMessage → 发请求 → 从 ChatResponse 里取 aiMessage().text()。
 * 4. api-key 没配置时,GlmConfig 会抛出带操作指引的异常,照着提示配 application-local.yml 即可。
 */
public class Example1_QuickStart {

    public static void main(String[] args) {
        // 1. 创建模型:OpenAI 协议 + GLM 端点(配置读取见 GlmConfig / GlmModels)
        ChatModel model = GlmModels.createChatModel();

        // 2. 便捷写法:一句话问过去,拿一句话回来
        String answer = model.chat("用一句话解释什么是大语言模型。");
        System.out.println("便捷写法回复:\n" + answer);

        // 3. 同一个模型可以连续多次调用,每次调用相互独立(没有记忆,记忆见示例 5)
        String answer2 = model.chat("Java 的三大特性是什么?每个用 5 个字以内概括。");
        System.out.println("\n第二次调用回复:\n" + answer2);
    }
}
