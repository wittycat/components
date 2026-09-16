package com.wittycat.pgvector;

import java.util.List;

import com.wittycat.SpringAiApplication;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 示例 12:检索 —— 问题也转成向量,按余弦距离找最相近的 chunk。
 * score 越接近 1 越相似。只检索不生成,这是 RAG 的前半段。
 * 和示例 7 的区别:向量在 PG 里走 HNSW 索引做近邻检索,不再是一次性内存全量余弦。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.pgvector.Example12_Search
 * 前置:先跑 Example11_Ingest
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
public class Example12_Search {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SpringAiApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            VectorStore vectorStore = ctx.getBean(VectorStore.class);

            List<Document> hits = vectorStore.similaritySearch(
                    SearchRequest.builder().query("HNSW 索引有什么特点").topK(3).build());

            for (Document d : hits) {
                System.out.printf("score=%.4f  %s…%n%n", d.getScore(), left(d.getText(), 50));
            }
        }
    }

    private static String left(String s, int n) {
        return s == null ? "" : s.substring(0, Math.min(n, s.length())).replace("\n", " ");
    }
}
