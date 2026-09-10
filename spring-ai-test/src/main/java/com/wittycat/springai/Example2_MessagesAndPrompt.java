package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 示例 2:消息角色、系统提示词、Prompt 模板、token 用量。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example2_MessagesAndPrompt
 *
 * 【知识点】
 * 1. 消息角色:system(设定身份/规则,优先级最高)、user(用户输入)、
 *    assistant(模型的历史回复,多轮时由框架自动带上)。一次请求 = 多种角色的消息列表。
 * 2. 系统提示词 .system(...) 是控制模型行为的第一杠杆:同一句 user 提问,
 *    配上不同 system 会得到完全不同风格的回答。
 * 3. Prompt 模板:Spring AI 默认用 {变量} 占位符(StTemplate 语法),通过 .param() 注入值,
 *    把"固定话术"和"动态内容"分离,是工程化组装提示词的标准做法。
 * 4. token 用量:call().chatResponse() 拿到完整 ChatResponse,元数据里的 Usage
 *    记录本次请求/回复的 token 消耗——计费和上下文长度控制都看它。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example2_MessagesAndPrompt {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        ChatClient chatClient = ChatClient.create(GlmModels.createChatModel());

        // 1. 系统提示词:设定角色和行为约束
        String answer = chatClient.prompt()
                .system("你是一位严谨的 Java 面试官,回答控制在 100 字以内,只讲要点。")
                .user("Spring 的 IOC 是什么?")
                .call()
                .content();
        System.out.println("带系统提示词的回复:\n" + answer);

        // 2. Prompt 模板:{topic} {tone} 是占位符,param() 注入实际值
        String templated = chatClient.prompt()
                .user(u -> u.text("给我推荐一本关于 {topic} 的入门书,并用 {tone} 的语气写 3 句推荐语。")
                        .param("topic", "Java 并发")
                        .param("tone", "幽默"))
                .call()
                .content();
        System.out.println("\n模板组装的回复:\n" + templated);

        // 3. 完整响应:回答 + token 用量
        ChatResponse response = chatClient.prompt()
                .user("用一句话解释什么是向量检索。")
                .call()
                .chatResponse();
        Usage usage = response.getMetadata().getUsage();
        System.out.println("\n完整响应:\n" + response.getResult().getOutput().getText());
        System.out.printf("token 用量: 输入 %d + 输出 %d = 总计 %d%n",
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }
}
