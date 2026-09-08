package com.wittycat.langchain4j;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

import java.util.Map;

/**
 * 示例 10:RAG 高级管线 —— 查询改写 + 知识库路由,组装自己的 RetrievalAugmentor。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example10_AdvancedRag
 *
 * 【知识点】
 * 1. LangChain4j 的 RAG 管线分四阶段(都在 DefaultRetrievalAugmentor 里,示例 8 用的默认配置):
 *      Transform(改写查询) → Route(选知识库) → Retrieve(检索) → Fuse(合并结果)。
 *    本例自定义前两阶段,见识一下"管线可插拔"。
 * 2. CompressingQueryTransformer(查询改写):多轮对话里用户会问"它怎么用?"。
 *    改写器结合聊天历史,用模型把"它"还原成完整问题再拿去检索——
 *    没有这一步,检索器收到的是只有代词的烂查询。
 * 3. LanguageModelQueryRouter(知识库路由):本例有两个知识库(Java/LangChain4j 文档、
 *    AI 通用概念),路由器让模型先判断问题属于哪个领域,只去对应知识库检索——
 *    知识库一多,全量检索既慢又会引入无关内容。
 * 4. 组装:queryTransformer + queryRouter 塞进 DefaultRetrievalAugmentor,
 *    再挂到 AiServices 的 .retrievalAugmentor()。注意示例 8 的 .contentRetriever(retriever)
 *    只是它的"单知识库简化形态"——挂了 retrievalAugmentor 就不要挂 contentRetriever。
 * 5. 两个运行现象值得观察:
 *      a) 第二问"它的第一个阶段解决什么问题?"——检索日志(若开启)能看到查询被改写成了
 *         完整问题;这就是改写器在起作用;
 *      b) 第三个问题被路由到了 AI 知识库(知识来源不同)。
 */
public class Example10_AdvancedRag {

    /** 带记忆的问答接口:记忆 + 高级 RAG 管线同时生效 */
    interface TechExpert {

        String chat(@MemoryId String sessionId, @UserMessage String question);
    }

    public static void main(String[] args) {
        ChatModel chatModel = GlmModels.createChatModel();
        EmbeddingModel embeddingModel = GlmModels.createEmbeddingModel();

        // 1. 两个知识库(复用示例 8 的入库封装)
        var langchain4jRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(Example8_RagAiServices.ingest("rag/knowledge-java.txt", embeddingModel))
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();
        var aiConceptRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(Example8_RagAiServices.ingest("rag/knowledge-ai.txt", embeddingModel))
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        // 2. 阶段一 Transform:结合聊天历史,把指代性追问改写成独立完整的问题
        var queryTransformer = new CompressingQueryTransformer(chatModel);

        // 3. 阶段二 Route:让模型判断问题属于哪个知识库,只路由过去
        LanguageModelQueryRouter queryRouter = LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .retrieverToDescription(Map.of(
                        langchain4jRetriever, "Java 语言与 LangChain4j 框架的技术文档",
                        aiConceptRetriever, "人工智能基础概念:RAG、Embedding、Transformer、Agent、幻觉等"))
                .build();

        // 4. 组装管线,挂进 AiServices
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .build();

        TechExpert expert = AiServices.builder(TechExpert.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(20).build())
                .retrievalAugmentor(augmentor)
                .build();

        // 5. 第一轮:LangChain4j 知识库
        String q1 = "LangChain4j 的 RAG 管线分哪几个阶段?";
        System.out.println("[问] " + q1);
        System.out.println("[答] " + expert.chat("s1", q1) + "\n");

        // 6. 第二轮:指代消解 —— "它"指上一轮的 RAG 管线,改写器会补全后再检索
        String q2 = "它的第一个阶段是为了解决什么问题?";
        System.out.println("[问] " + q2);
        System.out.println("[答] " + expert.chat("s1", q2) + "\n");

        // 7. 换个话题:应被路由到 AI 概念知识库
        String q3 = "什么是幻觉?为什么说 RAG 能缓解它?";
        System.out.println("[问] " + q3);
        System.out.println("[答] " + expert.chat("s2", q3));
    }
}
