package com.wittycat.pgvector;

import com.wittycat.SpringAiApplication;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 示例 10:最裸的 embedding —— 文本变成一个浮点数组,语义相近的文本向量夹角小。
 * (示例 7 开头也打印过 embedding 数组,这里单独把"相似度"量出来,给灌库/检索做铺垫)
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.pgvector.Example10_Embedding
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
public class Example10_Embedding {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SpringAiApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            EmbeddingModel embeddingModel = ctx.getBean(EmbeddingModel.class);

            float[] a = embeddingModel.embed("pgvector 是 PostgreSQL 的向量检索扩展");
            float[] b = embeddingModel.embed("PostgreSQL 可以用 pgvector 存储并检索 embedding 向量");
            float[] c = embeddingModel.embed("今天中午吃什么");

            System.out.println("向量维度 = " + a.length);
            System.out.printf("语义相关的两句相似度 = %.4f%n", cosine(a, b));
            System.out.printf("语义无关的两句相似度 = %.4f%n", cosine(a, c));
        }
    }

    /** 余弦相似度 = (a·b)/(|a|×|b|),1 表示方向完全相同。pgvector 的余弦距离 = 1 - 该值 */
    private static double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
