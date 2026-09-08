package com.wittycat.springaialibaba;

import org.springframework.ai.chat.client.ChatClient;

/**
 * 示例 1:最小可用对话 —— 用 DashScope starter 接通义千问,和 spring-ai-test 的示例 1 对照着看。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springaialibaba.Example1_QuickStart
 *
 * 【知识点】
 * 1. 换成 Spring AI Alibaba 后,"一次对话"的代码一个字都没变:还是 ChatClient.prompt().user().call().content()。
 *    因为 DashScopeChatModel 实现的就是 Spring AI 的 ChatModel 接口,框架是叠加关系,不是替换关系。
 * 2. 变化的只在"接线"层:
 *      - spring-ai-test: spring-ai-starter-model-openai + 智谱 OpenAI 兼容端点,要手动对齐
 *        base-url 是否带 /v1、completions-path 等细节;
 *      - 本模块:      spring-ai-alibaba-starter-dashscope 走 DashScope 原生协议,
 *        端点是默认值,配置里真正必填的只有 api-key 和 model。
 * 3. 默认模型 qwen-plus(商用主力);换 qwen-max(更强)/qwen-turbo(更便宜)只改 application-local.yml
 *    里的 dashscope.model,代码不动。
 */
public class Example1_QuickStart {

    public static void main(String[] args) {
        // CLI 示例不启动 Spring,手动构建模型;Web 示例里这一步由 starter 自动配置完成
        ChatClient chatClient = ChatClient.create(DashScopeModels.createChatModel());

        String question = "用一句话介绍 Spring AI Alibaba 和 Spring AI 的关系";
        System.out.println("问题: " + question + "\n");

        String answer = chatClient.prompt()
                .user(question)
                .call()
                .content();

        System.out.println("模型回答:\n" + answer);
    }
}
