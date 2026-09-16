package com.wittycat.pgvector.web;

import com.wittycat.pgvector.rag.KnowledgeBase;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Web 版完整 RAG 的三件套接口。
 *
 * 运行方式(和示例 9 的 SSE 同一个启动类 SpringAiApplication,8084 一个端口全包):
 *   mvn -q spring-boot:run
 *
 *   POST /rag/ingest          重新灌库(先清旧数据,可重复执行)
 *   GET  /rag/search?q=xxx    只检索不生成:看命中的 chunk 和分数(RAG 前半段)
 *   GET  /rag/chat?q=xxx      检索 + 拼上下文 + GLM 回答(完整 RAG)
 *
 * curl 示例:
 *   curl -X POST http://127.0.0.1:8084/rag/ingest
 *   curl "http://127.0.0.1:8084/rag/search?q=HNSW%20索引"
 *   curl "http://127.0.0.1:8084/rag/chat?q=切块太大或太小有什么问题"
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
@RestController
public class RagController {

    private final KnowledgeBase knowledgeBase;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagController(KnowledgeBase knowledgeBase, VectorStore vectorStore, ChatClient.Builder builder) {
        this.knowledgeBase = knowledgeBase;
        this.vectorStore = vectorStore;
        // QuestionAnswerAdvisor 在每次请求时自动:问题 → 向量检索 topK → 拼进提示词
        this.chatClient = builder
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    @PostMapping("/rag/ingest")
    public Map<String, Object> ingest() throws IOException {
        return Map.of("chunks", knowledgeBase.ingest());
    }

    @GetMapping("/rag/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(3).build())
                .stream()
                .map((Document d) -> Map.<String, Object>of("score", d.getScore(), "text", d.getText()))
                .toList();
    }

    @GetMapping("/rag/chat")
    public Map<String, String> chat(@RequestParam String q) {
        String answer = chatClient.prompt().user(q).call().content();
        return Map.of("answer", answer);
    }
}
