package com.agent.rag;

import com.agent.config.AgentConfig;
import com.agent.dto.KnowledgeDocumentDto;
import com.agent.entity.KnowledgeChunk;
import com.agent.repository.KnowledgeChunkRepository;
import com.agent.service.GlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentStore documentStore;
    private final GlmClient glmClient;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;

    /** 知识库文档清单（供 Agent 注入系统提示，让模型知道用户上传过哪些文档） */
    public List<KnowledgeDocumentDto> listDocuments() {
        return documentStore.listDocuments();
    }

    /**
     * 根据用户问题检索相关知识块（内存安全：不加载全表）
     */
    public List<String> retrieve(String query) {
        log.info("[RAG] 开始检索, query='{}', topK={}", query, agentConfig.getRagTopK());

        // 文件名直连：用户点名某份知识库文档时直接取该文档分块。
        // 纯文件名查询（如 "AGENTS.md"）与正文的向量相似度天然偏低、正文也不含文件名本身，
        // 语义/关键词检索都会落空
        List<String> byFilename = retrieveByFilename(query);
        if (!byFilename.isEmpty()) {
            log.info("[RAG] 命中知识库文件名，直接返回该文档分块");
            return byFilename;
        }

        int topK = agentConfig.getRagTopK();
        long totalChunks = chunkRepository.count();
        log.info("[RAG] 知识库总分块数: {}", totalChunks);

        if (totalChunks == 0) {
            log.info("[RAG] 知识库为空，返回空结果");
            return Collections.emptyList();
        }

        // 分块数量较少且存在 embedding 时尝试向量检索
        if (totalChunks <= agentConfig.getMaxVectorSearchChunks()) {
            log.info("[RAG] 尝试向量检索, 分块上限={}, 实际分块数={}", agentConfig.getMaxVectorSearchChunks(), totalChunks);
            List<KnowledgeChunk> embeddedChunks = chunkRepository.findChunksWithEmbedding(
                    agentConfig.getMaxVectorSearchChunks());
            if (!embeddedChunks.isEmpty()) {
                log.info("[RAG] 找到 {} 个带 embedding 的分块", embeddedChunks.size());
                List<Double> queryEmbedding = glmClient.getEmbedding(truncate(query, 500));
                if (queryEmbedding != null) {
                    List<String> results = vectorSearch(embeddedChunks, queryEmbedding, topK);
                    if (!results.isEmpty()) {
                        log.info("[RAG] 向量检索成功，返回 {} 个结果", results.size());
                        return results;
                    }
                }
            }
            log.info("[RAG] 向量检索未返回结果，降级为关键词搜索");
        }

        List<String> keywordResults = keywordSearch(query, topK);
        log.info("[RAG] 关键词搜索返回 {} 个结果", keywordResults.size());
        return keywordResults;
    }

    private List<String> vectorSearch(List<KnowledgeChunk> chunks, List<Double> queryEmbedding, int topK) {
        double threshold = agentConfig.getRagSimilarityThreshold();
        log.info("[RAG] 开始向量检索, 候选分块数={}, topK={}, 相似度阈值={}", chunks.size(), topK, threshold);
        List<ScoredChunk> scored = new ArrayList<>();

        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getEmbedding() == null) continue;
            try {
                List<Double> embedding = objectMapper.readValue(chunk.getEmbedding(),
                        new TypeReference<List<Double>>() {});
                double similarity = cosineSimilarity(queryEmbedding, embedding);
                scored.add(new ScoredChunk(chunk, similarity));
            } catch (Exception e) {
                log.warn("[RAG] 解析 embedding 失败, chunkId={}", chunk.getId());
            }
        }

        // 低于阈值视为不相关：宁可不返回结果降级为关键词搜索，也不把无关资料塞给模型
        List<ScoredChunk> relevant = scored.stream()
                .filter(s -> s.score() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
        if (relevant.isEmpty()) {
            double maxScore = scored.stream().mapToDouble(ScoredChunk::score).max().orElse(0);
            log.info("[RAG] 最高相似度={} 低于阈值={}，无相关结果", maxScore, threshold);
            return Collections.emptyList();
        }

        // 邻块扩展：详解类问题通常跨多个分块，命中块的下一分块（同文档相邻、内容连续）一并带入，
        // 避免只检索到章节前半部分；总数仍受 topK 约束
        Map<String, KnowledgeChunk> byDocAndIndex = new HashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            byDocAndIndex.put(chunk.getDocumentId() + "#" + chunk.getChunkIndex(), chunk);
        }
        List<KnowledgeChunk> selected = new ArrayList<>();
        for (ScoredChunk hit : relevant) {
            if (!selected.contains(hit.chunk())) {
                selected.add(hit.chunk());
            }
            if (selected.size() >= topK) break;
            KnowledgeChunk next = byDocAndIndex.get(hit.chunk().getDocumentId() + "#" + (hit.chunk().getChunkIndex() + 1));
            if (next != null && !selected.contains(next)) {
                selected.add(next);
            }
        }
        log.info("[RAG] 向量检索完成, 命中 {} 块, 邻块扩展后共 {} 块", relevant.size(), selected.size());
        return selected.stream().map(KnowledgeChunk::getContent).collect(Collectors.toList());
    }

    private List<String> keywordSearch(String query, int topK) {
        log.info("[RAG] 开始关键词搜索, query='{}', topK={}", query, topK);
        // 优先 FULLTEXT 检索（ngram 分词支持中文；有限制条数，不会全表加载）
        try {
            List<KnowledgeChunk> results = chunkRepository.searchByKeyword(query, topK);
            if (!results.isEmpty()) {
                log.info("[RAG] FULLTEXT 检索成功，返回 {} 个结果", results.size());
                return results.stream().map(KnowledgeChunk::getContent).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("[RAG] FULLTEXT 检索失败，降级为 LIKE 模糊匹配: {}", e.getMessage());
        }

        // 降级：剥离疑问词后按候选关键词逐个 LIKE 模糊匹配（仍有限制条数）
        for (String keyword : extractKeywords(query)) {
            List<KnowledgeChunk> results = chunkRepository.searchByLike(keyword, topK);
            if (!results.isEmpty()) {
                log.info("[RAG] LIKE 模糊匹配成功, 关键词='{}', 返回 {} 个结果", keyword, results.size());
                return results.stream().map(KnowledgeChunk::getContent).collect(Collectors.toList());
            }
        }

        log.info("[RAG] 关键词搜索未找到匹配结果");
        return Collections.emptyList();
    }

    /** 疑问词/程度词停用表，检索前剥离，避免整句 LIKE 永远匹配不上（长词在前，防止被子串截断） */
    private static final List<String> STOP_WORDS = List.of(
            "完整详解", "详细讲解", "介绍一下", "什么是", "为什么", "详解", "完整",
            "详细", "介绍", "说明", "解释", "怎么", "怎样", "如何", "哪些",
            "两大", "区别", "对比", "请", "一下", "谢谢");

    /**
     * 从问句中提取候选关键词：剥离停用词后按长度降序排列，供 LIKE 逐个尝试
     */
    private List<String> extractKeywords(String query) {
        // 单字结构助词/连词视作分词边界（中文无空格，不切开会导致整句成词、LIKE 匹配不上）
        String cleaned = query.replaceAll("[？?！!。，,、\\s的了吗呢吧和与或]+", " ").trim();
        for (String stop : STOP_WORDS) {
            cleaned = cleaned.replace(stop, " ");
        }
        List<String> keywords = Arrays.stream(cleaned.split("\\s+"))
                .filter(w -> w.length() >= 2)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .distinct()
                .collect(Collectors.toList());
        log.info("[RAG] 提取关键词: {}", keywords);
        return keywords;
    }

    /** 问句中包含完整文档文件名时，返回该文档的前 topK 个分块 */
    private List<String> retrieveByFilename(String query) {
        for (KnowledgeDocumentDto doc : documentStore.listDocuments()) {
            String name = doc.getFilename();
            if (name == null || name.isBlank() || !query.contains(name)) continue;
            log.info("[RAG] 问句点名文档: {}", name);
            return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId()).stream()
                    .limit(agentConfig.getRagTopK())
                    .map(KnowledgeChunk::getContent)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }

    public String buildContextPrompt(List<String> chunks) {
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("以下是从知识库中检索到的相关信息：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("【资料 ").append(i + 1).append("】\n").append(chunks.get(i)).append("\n\n");
        }
        sb.append("请基于以上资料回答用户问题。如果资料中没有相关信息，请如实说明。\n");
        return sb.toString();
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {}
}
