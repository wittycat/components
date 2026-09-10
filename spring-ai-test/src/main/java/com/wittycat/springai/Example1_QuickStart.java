package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;

/**
 * 示例 1:最小可用 —— 创建 ChatClient、问一句话、拿到回答。
 *
 * 运行方式(二选一):
 *   1. IDE 里直接运行 main 方法
 *   2. 命令行: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example1_QuickStart
 *
 * 【知识点】
 * 1. Spring AI 的定位:Spring 官方的 AI 应用框架,把"自动配置、starter、依赖注入"这套
 *    Spring 习惯带到 AI 开发里。和 agentscope-test(Agent 框架)、langgraph4j-test(图原语)、
 *    langchain4j-test(接口抽象 + 可组合中间件)对比着学,四种抽象层级,概念完全互通。
 * 2. ChatClient 是 Spring AI 的高层门面:prompt() 组装请求 → user() 提供问题 →
 *    call() 同步调用 → content() 取回答文本。底层是 ChatModel 接口(见 GlmModels),
 *    门面负责编排,模型负责执行,分层清晰。
 * 3. ChatClient.create(model) 是便捷工厂;要预设系统提示词、Advisor 时改用
 *    ChatClient.builder(model)....build()(示例 5/8 会用到)。
 * 4. api-key 没配置时,GlmConfig 会抛出带操作指引的异常,照着提示配 application-local.yml 即可。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example1_QuickStart {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        // 1. 创建模型:OpenAI 协议 + GLM 端点(配置读取见 GlmConfig / GlmModels)
        ChatClient chatClient = ChatClient.create(GlmModels.createChatModel());

        // 2. 便捷写法:链式组装一次请求,拿回答文本
        String answer = chatClient.prompt()
                .user("用一句话解释什么是大语言模型。")
                .call()
                .content();
        System.out.println("便捷写法回复:\n" + answer);

        // 3. 同一个 ChatClient 可以连续多次调用,每次调用相互独立(没有记忆,记忆见示例 5)
        String answer2 = chatClient.prompt()
                .user("Java 的三大特性是什么?每个用 5 个字以内概括。")
                .call()
                .content();
        System.out.println("\n第二次调用回复:\n" + answer2);
    }
}
