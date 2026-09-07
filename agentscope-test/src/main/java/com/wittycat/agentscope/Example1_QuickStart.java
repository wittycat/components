package com.wittycat.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;

/**
 * 示例 1:最小可用的 Agent —— 创建、调用、拿到回复。
 *
 * 运行方式(二选一):
 *   1. IDE 里直接运行 main 方法
 *   2. 命令行: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example1_QuickStart
 *
 * 【知识点】
 * 1. ReActAgent 是 AgentScope 的核心 Agent,名字来自 ReAct 模式:
 *      Re(Reasoning 推理) + Act(Acting 行动/调工具) 循环,直到产出最终答案。
 *    本例没有注册任何工具,所以它就是一个"会聊天的大模型客户端"——这是理解 Agent 的起点。
 * 2. call() 返回 Reactor 的 Mono<Msg>(响应式风格)。学习/脚本场景用 .block() 转成同步调用最直观;
 *    后面的示例 4 会展示不 block、逐事件消费的流式用法。
 * 3. RuntimeContext 携带 sessionId/userId 等运行时信息。同一个 sessionId 的多次调用共享
 *    会话状态(记忆),示例 3 会专门演示。
 * 4. 回复 Msg 是 AgentScope 的统一消息对象(role + 内容块列表),getTextContent() 取纯文本。
 */
public class Example1_QuickStart {

    public static void main(String[] args) {
        // 1. 创建 Agent:名字、系统提示词、模型,三要素
        ReActAgent agent = ReActAgent.builder()
                .name("assistant")
                .sysPrompt("你是一个友好的 AI 助手,用简洁的中文回答问题。")
                .model(ModelFactory.createGlmChatModel())
                .build();

        // 2. 运行时上下文:标识这次会话
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("quickstart-demo")
                .userId("learner")
                .build();

        // 3. 发起一次调用,block() 阻塞直到拿到回复
        //    call() 有两个常用重载:传 String(便捷,内部包成 UserMessage)或 List<Msg>(完整控制消息列表)
        String question = "用一句话介绍什么是 AI Agent";
        System.out.println(">>> 用户: " + question);
        Msg reply = agent.call(question, ctx).block();

        // 4. 取出回复文本
        System.out.println("<<< " + agent.getName() + ": " + reply.getTextContent());
    }
}
