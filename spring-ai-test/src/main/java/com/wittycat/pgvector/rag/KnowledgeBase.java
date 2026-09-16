package com.wittycat.pgvector.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 知识库灌库组件:CLI 示例 11 和 Web 端 /rag/ingest 共用。
 *
 * 灌库三步 = 读语料 → 切块 → add(add 内部对每个 chunk 调 embedding 模型再写 PG)。
 * 先 DELETE 再写,保证重复执行不产生重复数据。
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
@Component
public class KnowledgeBase {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBase(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return 写入的 chunk 数量 */
    public int ingest() throws IOException {
        String text = new String(
                new ClassPathResource("rag/pgvector-notes.txt").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        // vector_store 是 PgVectorStore 的默认表名,可用 spring.ai.vectorstore.pgvector.table-name 改
        jdbcTemplate.update("DELETE FROM vector_store");

        // 200 token 一块(默认 800,短文档切不开,检索粒度太粗),块间保留重叠
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(200)
                .withKeepSeparator(true)
                .build()
                .apply(List.of(new Document(text, Map.of("source", "pgvector-notes"))));
        vectorStore.add(chunks);
        return chunks.size();
    }
}
