package com.wittycat.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;

/**
 * 示例 3:多轮对话记忆 —— Agent 为什么"记得"你说过的话。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example3_Memory
 *
 * 【知识点】
 * 1. 大模型本身是无状态的:每次请求都要把"历史对话"一起发过去,模型才能联系上下文。
 *    AgentScope 的价值之一就是帮你管理这件事——Agent 按 sessionId 保存会话的 Msg 列表,
 *    每次 call() 自动把历史消息和本次输入一起发给模型。
 * 2. 一个 Agent 实例可以同时服务多个会话:sessionId 不同,记忆完全隔离(类似 Web 里的 session)。
 * 3. clearContext(ctx) 清空指定会话的记忆,等价于聊天应用里的"开新对话"按钮。
 */
public class Example3_Memory {

    public static void main(String[] args) {
        ReActAgent agent = ReActAgent.builder()
                .name("memory-agent")
                .sysPrompt("你是一个助手,请记住用户告诉你的信息,回答保持简洁。")
                .model(ModelFactory.createGlmChatModel())
                .build();

        // 会话 A:同一个 sessionId 的多次调用共享记忆
        RuntimeContext sessionA = RuntimeContext.builder().sessionId("session-A").userId("learner").build();

        // 第一轮:告诉它一些个人信息
        String turn1 = "你好,我叫小明,是一名 Java 工程师,正在学习 AI Agent 开发。";
        System.out.println(">>> 用户: " + turn1);
        printlnReply(agent.call(turn1, sessionA).block());

        // 第二轮:同会话追问,模型能"记得"上面的信息
        String turn2 = "我叫什么名字?在学什么?";
        System.out.println("\n>>> 用户: " + turn2);
        printlnReply(agent.call(turn2, sessionA).block());

        // 第三轮:换一个 sessionId —— 全新会话,记忆隔离,应该"不认识"小明
        RuntimeContext sessionB = RuntimeContext.builder().sessionId("session-B").userId("learner").build();
        String turn3 = "你知道我叫什么名字吗?";
        System.out.println("\n>>> 用户(新会话 session-B): " + turn3);
        printlnReply(agent.call(turn3, sessionB).block());

        // 第四轮:清空会话 A 的记忆再问,同样"失忆"了
        agent.clearContext(sessionA);
        System.out.println("\n>>> 用户(已清空 session-A 记忆): 我叫什么名字?");
        printlnReply(agent.call("我叫什么名字?", sessionA).block());
    }

    private static void printlnReply(Msg reply) {
        System.out.println("<<< " + reply.getTextContent());
    }
}
