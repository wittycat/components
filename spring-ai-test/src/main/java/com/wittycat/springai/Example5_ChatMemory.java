package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

/**
 * 示例 5:会话记忆 —— 让"无状态"的模型记住上下文,且多会话互不串台。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example5_ChatMemory
 *
 * 【知识点】
 * 1. 大模型 API 本身无状态,"记得" = 框架把历史消息随每次请求一起重发。
 *    MessageWindowChatMemory 滑动窗口只保留最近 N 条,防止历史无限膨胀。
 * 2. MessageChatMemoryAdvisor 是"记忆挂载点":它是一个 Advisor(Spring AI 的拦截器,
 *    见示例 8 的知识点),请求前把记忆里的历史并入消息列表,响应后把新消息存回记忆。
 *    这就是 Spring AI 的风格——横切能力全部做成 Advisor,ChatClient 上按需挂载。
 * 3. 会话隔离:请求时通过 advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
 *    指定会话 ID,同一个 ID 共享记忆,不同 ID 互不干扰——多用户聊天接口的雏形。
 * 4. 对照 langchain4j-test 示例 5:那边用 @MemoryId 注解声明在接口参数上,
 *    这边用 CONVERSATION_ID 参数传递,思路一致,写法不同。
 * 5. 内存实现重启即失;生产环境给 ChatMemory 换持久化存储(如 Redis)即可,接口不变。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example5_ChatMemory {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        // 1. 记忆存储 + 记忆 Advisor:窗口 20 条,挂到 ChatClient 上全局生效
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        ChatClient chatClient = ChatClient.builder(GlmModels.createChatModel())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        // 2. 会话 s1:告诉它两件事
        String a1 = chatClient.prompt()
                .user("我叫小明,我最喜欢的数字是 7。")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s1"))
                .call()
                .content();
        System.out.println("[s1] " + a1);

        // 3. 会话 s2:全新会话,应该不知道小明的信息——验证记忆按会话隔离
        String a2 = chatClient.prompt()
                .user("我叫什么名字?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s2"))
                .call()
                .content();
        System.out.println("[s2] " + a2 + "   ← 新会话,记忆互不串台");

        // 4. 回到会话 s1:它能想起名字和数字——验证记忆还在
        String a3 = chatClient.prompt()
                .user("我叫什么名字?我最喜欢的数字乘以 6 等于多少?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s1"))
                .call()
                .content();
        System.out.println("[s1] " + a3 + "   ← 回到 s1,记忆还在");
    }
}
