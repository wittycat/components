package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * 示例 8:RAG 接入 Advisor —— "和文档对话"的正确姿势(示例 7 的工程化版本)。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example8_RagAdvisor
 *
 * 【知识点】
 * 1. QuestionAnswerAdvisor 把示例 7 的"检索 + 拼上下文"自动化:每次提问前它先去
 *    VectorStore 检索相关片段,按预置模板("资料 + 问题")改写请求再发给模型。
 *    业务代码只剩 .defaultAdvisors(...) 一行,和记忆 Advisor 自由组合——
 *    这就是 Advisor 链的价值:横切能力即插即用,类似 Servlet Filter / Spring AOP。
 * 2. Advisor 顺序:QuestionAnswerAdvisor 在前,MessageChatMemoryAdvisor 在后——
 *    存进记忆的是用户的原始问题(而非注入资料后的长消息),多轮对话记忆才干净。
 * 3. 记忆 + RAG 组合后,可以自然地追问:"它"这种指代词能结合上文理解,
 *    但注意检索用的是当前问题,多轮追问复杂时需要查询改写(见 langchain4j-test 示例 10 的思路)。
 * 4. 知识库外的问题:system 提示词约束"没有就说没有",配合 similarityThreshold 过滤,
 *    让模型老实回答"不知道"而不是编造。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example8_RagAdvisor {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        // 1. 建库入库(和示例 7 相同)
        VectorStore vectorStore = SimpleVectorStore.builder(GlmModels.createEmbeddingModel()).build();
        List<Document> chunks = new TokenTextSplitter().split(
                List.of(RagDocs.load("rag/knowledge-springai.txt")));
        vectorStore.add(chunks);
        System.out.println("知识库就绪,共 " + chunks.size() + " 段\n");

        // 2. ChatClient 上同时挂两个 Advisor:RAG 检索 + 会话记忆
        ChatClient chatClient = ChatClient.builder(GlmModels.createChatModel())
                .defaultSystem("仅根据资料回答;资料里没有的信息就直接说不知道,不要编造。")
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .build(),
                        MessageChatMemoryAdvisor.builder(
                                        MessageWindowChatMemory.builder().maxMessages(20).build())
                                .build())
                .build();

        // 3. 知识库内的问题:回答基于检索到的资料
        String a1 = chatClient.prompt()
                .user("Spring AI 的 Advisor 是什么机制?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s1"))
                .call()
                .content();
        System.out.println("[问] Spring AI 的 Advisor 是什么机制?");
        System.out.println("[答] " + a1 + "\n");

        // 4. 追问:靠记忆理解"它"指的是 Advisor,靠 RAG 补充细节
        String a2 = chatClient.prompt()
                .user("它和 Servlet Filter 有什么相似之处?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s1"))
                .call()
                .content();
        System.out.println("[问] 它和 Servlet Filter 有什么相似之处?");
        System.out.println("[答] " + a2 + "\n");

        // 5. 知识库外的问题:老实承认不知道
        String a3 = chatClient.prompt()
                .user("怎么用 Spring AI 发 HTTP 请求调用第三方支付接口?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s1"))
                .call()
                .content();
        System.out.println("[问] 怎么用 Spring AI 发 HTTP 请求调用第三方支付接口?");
        System.out.println("[答] " + a3);
    }
}
