package com.wittycat.langchain4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 示例 7:RAG 基础 —— 文档入库与手动检索,亲手把 RAG 的三个环节各跑一遍。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example7_RagBasics
 *
 * 【知识点】
 * 1. RAG 三环节:入库(切分 + 向量化 + 存储)→ 检索(问题也向量化,按相似度找最相关的几段)
 *    → 生成(把检索到的资料拼进提示词,让模型"看资料答题")。
 * 2. Embedding(向量化)是语义检索的坐标:语义相近的文本向量距离就近,
 *    所以"模型怎么记住的"不用关键词完全匹配也能搜到(试试换个问法)。
 * 3. DocumentSplitters.recursive(最大段长, 重叠):按段落递归切分,段与段之间保留重叠
 *    避免句子被切断后语义丢失。切分粒度直接影响检索质量,是 RAG 调优的第一杠杆。
 * 4. EmbeddingStoreIngestor 把"切分→向量化→入库"串成一步;InMemoryEmbeddingStore
 *    存在内存里,进程重启即失,生产换 PGVector/Milvus 等,接口不变。
 * 5. 示例 8 会看到:同样的检索逻辑,交给 ContentRetriever 后 AiServices 会自动做,
 *    不用手动拼提示词——本例的意义是让你看清框架底下发生了什么。
 */
public class Example7_RagBasics {

    public static void main(String[] args) {
        // ---------- 环节一:入库 ----------
        // 1. 加载文档(classpath 上的知识库 txt)
        Document doc = RagDocs.load("rag/knowledge-java.txt");

        // 2. 切分:每段最多 200 字,相邻段重叠 30 字
        var splitter = DocumentSplitters.recursive(200, 30);
        List<TextSegment> segments = splitter.split(doc);
        System.out.println("文档切成了 " + segments.size() + " 段");

        // 3. 向量化并入库(EmbeddingStoreIngestor 一步完成,内部就是 split + embed + addAll)
        EmbeddingModel embeddingModel = GlmModels.createEmbeddingModel();
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(doc);
        System.out.println("已向量化入库(模型: " + GlmConfig.embeddingModel() + ")\n");

        // ---------- 环节二:检索 ----------
        String question = "AiServices 是怎么工作的?";
        System.out.println("问题: " + question);

        // 问题本身也要向量化,才能和资料段比相似度
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(2)   // 取最相关的 2 段
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(searchRequest).matches();

        System.out.println("检索到的资料(分数越接近 1 越相关):");
        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.printf("  [%.4f] %s%n", match.score(), preview(match.embedded().text()));
        }

        // ---------- 环节三:生成 ----------
        // 把检索结果拼进提示词,让模型基于资料回答(RAG 的"增强"就体现在这一步)
        String context = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n"));
        PromptTemplate promptTemplate = PromptTemplate.from("""
                请只依据下面的资料回答问题,资料里没有的信息直接说"资料中未提到":
                ---
                {{context}}
                ---
                问题:{{question}}
                """);
        String prompt = promptTemplate.apply(Map.of("context", context, "question", question)).text();

        ChatModel chatModel = GlmModels.createChatModel();
        System.out.println("\n模型回答:\n" + chatModel.chat(prompt));
    }

    /** 文本太长时截前 60 字,方便控制台预览 */
    private static String preview(String text) {
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}
