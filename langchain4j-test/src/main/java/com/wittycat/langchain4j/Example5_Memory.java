package com.wittycat.langchain4j;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * 示例 5:会话记忆 —— 每个用户各自的上下文,互不串台。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example5_Memory
 *
 * 【知识点】
 * 1. 模型本身无状态(示例 2 手动拼过历史),"记忆"就是框架替你管理历史消息:
 *    每次 chat 时,框架先把记忆里的历史 + 新问题组装成消息列表,再发给模型。
 * 2. MessageWindowChatMemory 是最常用的记忆实现:滑动窗口,只保留最近 maxMessages 条消息,
 *    防止历史无限增长撑爆上下文窗口。(还有 TokenWindowChatMemory 按 token 数裁剪。)
 * 3. ChatMemoryProvider:按 memoryId 创建各自的记忆实例——Web 应用里每个 sessionId/用户
 *    一个记忆,多轮对话互不干扰。示例 11 的 SSE 接口就是这么做的。
 * 4. 接口方法上加 @MemoryId 标记"这个参数是会话 ID",它不会发给模型,只用于选记忆。
 * 5. 记忆是存在内存里的(进程重启即失)。生产环境可用 langchain4j-community 提供的
 *    Redis/数据库等持久化 ChatMemoryStore,接口是现成的,换实现即可。
 */
public class Example5_Memory {

    /** 带 @MemoryId 的接口:同一个 assistant 实例,多个会话各记各的 */
    interface ChatAssistant {

        String chat(@MemoryId String sessionId, @UserMessage String message);
    }

    public static void main(String[] args) {
        ChatAssistant assistant = AiServices.builder(ChatAssistant.class)
                .chatModel(GlmModels.createChatModel())
                // 每个 sessionId 第一次出现时,创建一个独立的滑动窗口记忆
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .build())
                .build();

        // 会话 s1:告诉它两件事
        System.out.println("[s1] " + assistant.chat("s1", "我叫小明,我最喜欢的数字是 7。"));
        System.out.println("\n[s1] " + assistant.chat("s1", "我叫什么名字?我最喜欢的数字是几?"));

        // 会话 s2:新会话,什么都不知道 —— 证明记忆是按 sessionId 隔离的
        System.out.println("\n[s2] " + assistant.chat("s2", "我叫什么名字?"));

        // 回到 s1:记忆还在
        System.out.println("\n[s1] " + assistant.chat("s1", "把我喜欢的数字乘以 6 等于多少?"));
    }
}
