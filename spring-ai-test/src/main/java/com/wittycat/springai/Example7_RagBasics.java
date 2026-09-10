package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 示例 7:RAG 基础 —— 切分 → 向量化入库 → 手动检索 → 拼上下文,三个环节亲手过一遍。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example7_RagBasics
 *
 * 【知识点】
 * 1. 模型只知道训练数据里的东西,你的私有文档它没见过;直接塞全文又会撑爆上下文窗口。
 *    RAG(检索增强生成)的思路:把文档切成片段、向量化存起来,提问时只检索最相关的
 *    几段塞进上下文——省 token 又准。
 * 2. 入库是 Spring AI 的 ETL 管线:Document(文档)→ TokenTextSplitter(按 token 切分的
 *    DocumentTransformer)→ VectorStore.add()(内部调用 EmbeddingModel 向量化后存储)。
 * 3. 检索:similaritySearch 按余弦相似度取 Top-N,SearchRequest 可设 topK 和
 *    similarityThreshold(低于阈值的结果直接过滤,是治幻觉的第一道闸)。
 *    Document.getScore() 返回相似度分数,越接近 1 越相关。
 * 4. SimpleVectorStore 是内存参考实现(重启即失);生产换 PGVector / Milvus / Redis,
 *    只是换一个 VectorStore Bean,检索代码一行不改。
 * 5. 本例最后一步手动拼提示词,把"资料 + 问题"一起发出去;示例 8 会把这个动作
 *    交给 QuestionAnswerAdvisor 自动完成。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example7_RagBasics {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        EmbeddingModel embeddingModel = GlmModels.createEmbeddingModel();

        // 1. 向量化原语:一段文本 → 一个 float 向量(语义检索的"坐标")
        float[] vector = embeddingModel.embed("什么是向量检索?");
        System.out.printf("embedding-3 把文本变成 %d 维向量,前 5 维: %s...%n",
                vector.length, java.util.Arrays.toString(java.util.Arrays.copyOf(vector, 5)));

        // 2. 入库:文档 → 切分 → 向量化存储(SimpleVectorStore 内部持有 embeddingModel)
        //    构造参数:每块约 150 token、块内最短 40 字符、最短可入库 5 字符;块越小检索越精准
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> chunks = new TokenTextSplitter(150, 40, 5, 10000, true,
                java.util.List.of('\n')).split(
                List.of(RagDocs.load("rag/knowledge-springai.txt")));
        vectorStore.add(chunks);
        System.out.println("知识库切成了 " + chunks.size() + " 段");

        // 3. 检索:取最相关的 3 段,相似度低于阈值的丢弃
        //    注意:不同向量模型的分数量级不同(embedding-3 的余弦分整体偏低),阈值要按模型调
        String question = "Spring AI 的 Advisor 是什么机制?";
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .similarityThreshold(0.3)
                        .build());

        System.out.println("\n问题: " + question);
        System.out.println("检索到的资料(分数越接近 1 越相关):");
        for (Document hit : hits) {
            System.out.printf("  [%.4f] %s...%n", hit.getScore(),
                    abbreviate(hit.getText(), 40));
        }

        // 4. 手动拼上下文,发给模型
        String context = hits.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        String answer = ChatClient.create(GlmModels.createChatModel()).prompt()
                .system("仅根据提供的资料回答问题;资料里没有的信息就直接说不知道,不要编造。")
                .user("资料:\n" + context + "\n\n问题:" + question)
                .call()
                .content();

        System.out.println("\n模型回答:\n" + answer);
    }

    private static String abbreviate(String text, int max) {
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
