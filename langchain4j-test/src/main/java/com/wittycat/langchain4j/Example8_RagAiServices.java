package com.wittycat.langchain4j;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

/**
 * 示例 8:RAG 接入 AiServices —— 几行代码实现"和文档对话"。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example8_RagAiServices
 *
 * 【知识点】
 * 1. 示例 7 手动的三步,这里收进框架:接口方法上加一个 ContentRetriever,
 *    AiServices 会在每次提问前自动 检索 → 注入上下文 → 调模型,业务代码里完全看不见提示词拼接。
 * 2. EmbeddingStoreContentRetriever 是最常用的检索器实现:向量库 + 向量模型 + 过滤条件
 *    (maxResults 最多取几条,minScore 相似度门槛,低于分数的直接丢弃)。
 * 3. minScore 的价值:第二个问题(Java 知识库里根本没有的内容)检索结果相似度很低,
 *    门槛一过滤,模型拿不到任何上下文,配合系统提示词就会老实回答"不知道"——
 *    这就是用 RAG 治"幻觉"的基本盘。
 * 4. AiServices 还支持 Tools + ContentRetriever + ChatMemory 同时挂载:
 *    记忆管上下文连续性,工具管动作,检索管知识,三者正交组合,这正是 LangChain4j
 *    "可组合"设计的好处。
 */
public class Example8_RagAiServices {

    /** 问答接口:返回类型是 String,但检索增强在框架内部自动完成 */
    interface DocAssistant {

        @SystemMessage("""
                你是 LangChain4j 文档助手,只依据提供的上下文回答问题。
                上下文里没有的内容,直接回答"我的知识库里没有这个内容",不要编造。
                """)
        String answer(String question);
    }

    public static void main(String[] args) {
        // 1. 知识库入库(同示例 7:切分 + 向量化 + 内存向量库)
        EmbeddingModel embeddingModel = GlmModels.createEmbeddingModel();
        EmbeddingStore<TextSegment> store = ingest("rag/knowledge-java.txt", embeddingModel);

        // 2. 检索器:最多 3 条,相似度低于 0.5 的丢弃
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        // 3. 组装:模型 + 检索器,比示例 7 少了所有手动环节
        DocAssistant assistant = AiServices.builder(DocAssistant.class)
                .chatModel(GlmModels.createChatModel())
                .contentRetriever(retriever)
                .build();

        // 4. 知识库里有的:模型基于检索到的资料回答
        String q1 = "MessageWindowChatMemory 是什么?怎么做到多用户隔离?";
        System.out.println("问: " + q1);
        System.out.println("答: " + assistant.answer(q1));

        // 5. 知识库里没有的:相似度被 minScore 过滤,模型应回答"没有这个内容"
        String q2 = "Spring Cloud Gateway 的限流配置怎么写?";
        System.out.println("\n问: " + q2);
        System.out.println("答: " + assistant.answer(q2));
    }

    /** 切分 + 向量化 + 入库,封装成方法方便复用 */
    static EmbeddingStore<TextSegment> ingest(String classpathLocation, EmbeddingModel embeddingModel) {
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(200, 30))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(RagDocs.load(classpathLocation));
        return store;
    }
}
