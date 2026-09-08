package com.wittycat.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 示例 2:消息角色、手动多轮、采样参数与提示词模板。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example2_MessagesAndPrompt
 *
 * 【知识点】
 * 1. 对话的本质是"消息列表":三种角色
 *      - SystemMessage  系统设定(人设/规则),通常放第一条;
 *      - UserMessage    用户输入;
 *      - AiMessage      模型此前的回答(手动多轮时由你自己塞回列表)。
 * 2. 大模型本身无状态:所谓"多轮对话",就是把历史消息原样重新发一遍。
 *    本例第 2 问里的"它"能被正确理解,靠的就是列表里那条 AiMessage——这就是"记忆"的原理
 *    (自动管理历史见示例 5)。
 * 3. ChatResponse 是结构化响应:除文本外还有 tokenUsage(本次消耗的输入/输出 token 数),
 *    做计费和限流时要用它。
 * 4. PromptTemplate:把"模板 + 变量"变成完整提示词,占位符语法是 {{变量名}},
 *    是提示词工程里"固定套路、只换参数"的标准做法。
 */
public class Example2_MessagesAndPrompt {

    public static void main(String[] args) {
        ChatModel model = GlmModels.createChatModel();

        // 1. 手动多轮:把历史消息(包括模型的回答)按顺序重新发给模型
        List<ChatMessage> history = new ArrayList<>();
        history.add(SystemMessage.from("你是一个尽量用一句话回答问题的助手。"));
        history.add(UserMessage.from("LangChain4j 是什么?"));
        history.add(AiMessage.from("LangChain4j 是一个把大语言模型能力(对话、工具、RAG 等)封装成 Java 接口的开源框架。"));
        history.add(UserMessage.from("它主要解决什么问题?"));

        ChatResponse response = model.chat(history);
        System.out.println("多轮对话回复(注意'它'被正确理解):");
        System.out.println(response.aiMessage().text());

        // 2. 结构化响应:token 用量(输入/输出各消耗多少 token)
        if (response.tokenUsage() != null) {
            System.out.println("\ntoken 用量: 输入 " + response.tokenUsage().inputTokenCount()
                    + " + 输出 " + response.tokenUsage().outputTokenCount());
        }

        // 3. 提示词模板:固定套路只换参数
        PromptTemplate template = PromptTemplate.from("""
                请把下面的句子翻译成{{language}},只给译文,不要解释:
                {{text}}
                """);
        String prompt = template.apply(Map.of(
                "language", "英文",
                "text", "天行健,君子以自强不息。")).text();
        System.out.println("\n提示词模板渲染后的翻译:\n" + model.chat(prompt));
    }
}
