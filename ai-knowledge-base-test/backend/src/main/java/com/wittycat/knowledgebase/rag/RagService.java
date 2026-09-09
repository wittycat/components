package com.wittycat.knowledgebase.rag;

import com.wittycat.knowledgebase.config.AgentConfig;
import com.wittycat.knowledgebase.dto.KnowledgeDocumentDto;
import com.wittycat.knowledgebase.entity.KnowledgeChunk;
import com.wittycat.knowledgebase.repository.KnowledgeChunkRepository;
import com.wittycat.knowledgebase.service.GlmClient;
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

    /** 送入 embedding 的查询文本最大长度（与分块尺寸同量级，超出截断） */
    private static final int QUERY_EMBED_MAX_CHARS = 500;

    /** 余弦相似度分母防零常量 */
    private static final double COSINE_EPSILON = 1e-10;

    /**
     * 根据用户问题检索相关知识块（内存安全：不加载全表）
     */
    public List<String> retrieve(String query) {
        log.info("[RAG] 开始检索, query='{}', topK={}", query, agentConfig.getRagTopK());

        // 文件名直连：用户点名某份知识库文档时直接取该文档分块。
        // 纯文件名查询（如 "AGENTS.md"）与正文的向量相似度天然偏低、正文也不含文件名本身，
        // 语义/关键词检索都会落空
        List<KnowledgeChunk> byFilename = retrieveByFilename(query);
        if (!byFilename.isEmpty()) {
            log.info("[RAG] 命中知识库文件名，直接返回该文档分块");
            return toContents(expandToWholeDocuments(byFilename));
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
                List<Double> queryEmbedding = glmClient.getEmbedding(truncate(query, QUERY_EMBED_MAX_CHARS));
                if (queryEmbedding != null) {
                    List<KnowledgeChunk> selected = vectorSearch(embeddedChunks, queryEmbedding, topK);
                    if (!selected.isEmpty()) {
                        log.info("[RAG] 向量检索成功，命中 {} 个结果", selected.size());
                        return toContents(expandToWholeDocuments(selected));
                    }
                }
            }
            log.info("[RAG] 向量检索未返回结果，降级为关键词搜索");
        }

        List<KnowledgeChunk> keywordResults = keywordSearch(query, topK);
        log.info("[RAG] 关键词搜索返回 {} 个结果", keywordResults.size());
        return toContents(expandToWholeDocuments(keywordResults));
    }

    private List<KnowledgeChunk> vectorSearch(List<KnowledgeChunk> chunks, List<Double> queryEmbedding, int topK) {
        double threshold = agentConfig.getRagSimilarityThreshold();
        log.info("[RAG] 开始向量检索, 候选分块数={}, topK={}, 相似度阈值={}", chunks.size(), topK, threshold);
        List<ScoredChunk> scored = new ArrayList<>();

        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getEmbedding() == null) {
                continue;
            }
            try {
                List<Double> embedding = objectMapper.readValue(chunk.getEmbedding(),
                        new TypeReference<List<Double>>() {});
                double similarity = cosineSimilarity(queryEmbedding, embedding);
                scored.add(new ScoredChunk(chunk, similarity));
            } catch (Exception e) {
                log.error("[RAG] 解析 embedding 失败, chunkId={}", chunk.getId(), e);
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

        // 邻块补全统一放在 expandToWholeDocuments 的超预算兜底分支：此处按相关度纯取 top-K，
        // 文档在预算内会整篇注入，不需要在此做邻块扩展
        List<KnowledgeChunk> selected = relevant.stream()
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());
        log.info("[RAG] 向量检索完成, 命中 {} 块", selected.size());
        return selected;
    }

    private List<KnowledgeChunk> keywordSearch(String query, int topK) {
        log.info("[RAG] 开始关键词搜索, query='{}', topK={}", query, topK);
        // 优先 FULLTEXT 检索（ngram 分词支持中文；有限制条数，不会全表加载）
        try {
            List<KnowledgeChunk> results = chunkRepository.searchByKeyword(query, topK);
            if (!results.isEmpty()) {
                log.info("[RAG] FULLTEXT 检索成功，返回 {} 个结果", results.size());
                return results;
            }
        } catch (Exception e) {
            log.error("[RAG] FULLTEXT 检索失败，降级为 LIKE 模糊匹配", e);
        }

        // 降级：剥离疑问词后按候选关键词逐个 LIKE 模糊匹配（仍有限制条数）
        for (String keyword : extractKeywords(query)) {
            List<KnowledgeChunk> results = chunkRepository.searchByLike(keyword, topK);
            if (!results.isEmpty()) {
                log.info("[RAG] LIKE 模糊匹配成功, 关键词='{}', 返回 {} 个结果", keyword, results.size());
                return results;
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

    /** 问句中包含完整文档文件名时，返回该文档的前 topK 个分块（后续仍会尝试整篇扩展） */
    private List<KnowledgeChunk> retrieveByFilename(String query) {
        for (KnowledgeDocumentDto doc : documentStore.listDocuments()) {
            String name = doc.getFilename();
            if (name == null || name.isBlank() || !query.contains(name)) {
                continue;
            }
            log.info("[RAG] 问句点名文档: {}", name);
            return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId()).stream()
                    .limit(agentConfig.getRagTopK())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 整篇文档扩展：命中分块所属文档的全文不超过预算时，改为注入该文档全部分块（按 chunkIndex 顺序）。
     * 只取 top-K 片段时，详解类问题常在分块边界处截断——文档明明写了"第 3、4 层"，
     * 模型却只能说"资料在此处截断、未提及"。预算按文档首次命中顺序分配；装不下（超预算）的文档
     * 退回命中分块并补上下相邻分块，保证跨块内容（列表、表格、连续段落）不断裂。
     */
    private List<KnowledgeChunk> expandToWholeDocuments(List<KnowledgeChunk> hits) {
        if (hits.isEmpty()) {
            return hits;
        }
        // 预算 <=0 视为不限制：命中的文档一律整篇注入（此前 "预算<=0 关闭扩展" 的语义
        // 会静默退回纯 top-K、重新引入跨块截断，已废弃）
        int budget = agentConfig.getRagWholeDocMaxChars() <= 0
                ? Integer.MAX_VALUE
                : agentConfig.getRagWholeDocMaxChars();

        // 文档全部分块只需查一次；LinkedHashMap 保持首次命中（相关度）顺序
        Map<Long, List<KnowledgeChunk>> docs = new LinkedHashMap<>();
        for (KnowledgeChunk hit : hits) {
            docs.computeIfAbsent(hit.getDocumentId(),
                    docId -> new ArrayList<>(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)));
        }

        List<KnowledgeChunk> result = new ArrayList<>();
        int used = 0;
        for (Map.Entry<Long, List<KnowledgeChunk>> entry : docs.entrySet()) {
            List<KnowledgeChunk> fullDoc = entry.getValue();
            int docChars = fullDoc.stream()
                    .mapToInt(c -> c.getContent() == null ? 0 : c.getContent().length())
                    .sum();
            if (docChars <= budget - used) {
                used += docChars;
                result.addAll(fullDoc);
                log.info("[RAG] 文档 {} 全文 {} 字符在预算内，整篇注入（{} 块）",
                        entry.getKey(), docChars, fullDoc.size());
            } else {
                List<KnowledgeChunk> docHits = hits.stream()
                        .filter(c -> c.getDocumentId().equals(entry.getKey()))
                        .collect(Collectors.toList());
                // 超预算退回命中分块时补上下相邻分块：详解内容常跨分块边界
                // （如四层列表前两层在块尾、后两层在下一块头），只给命中块模型只能回答"资料被截断"
                Map<Integer, KnowledgeChunk> byIndex = new HashMap<>();
                for (KnowledgeChunk c : fullDoc) {
                    byIndex.put(c.getChunkIndex(), c);
                }
                List<KnowledgeChunk> expanded = new ArrayList<>();
                for (KnowledgeChunk hit : docHits) {
                    for (int idx = hit.getChunkIndex() - 1; idx <= hit.getChunkIndex() + 1; idx++) {
                        KnowledgeChunk c = byIndex.get(idx);
                        if (c != null && !expanded.contains(c)) {
                            expanded.add(c);
                        }
                    }
                }
                expanded.sort(Comparator.comparingInt(KnowledgeChunk::getChunkIndex));
                log.info("[RAG] 文档 {} 全文 {} 字符超出剩余预算 {}，保留命中分块及相邻块共 {} 块",
                        entry.getKey(), docChars, budget - used, expanded.size());
                result.addAll(expanded);
            }
        }
        log.info("[RAG] 整篇扩展完成，共注入 {} 块（预算 {} 字符）", result.size(), budget);
        return result;
    }

    private List<String> toContents(List<KnowledgeChunk> chunks) {
        return chunks.stream().map(KnowledgeChunk::getContent).collect(Collectors.toList());
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + COSINE_EPSILON);
    }

    public String buildContextPrompt(List<String> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("以下是从知识库检索到的、与用户问题相关的资料（用户上传文档的片段或全文）：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("【资料 ").append(i + 1).append("】\n").append(chunks.get(i)).append("\n\n");
        }
        sb.append("回答要求（优先级从高到低）：\n")
                .append("1. 必须优先、严格依据以上资料回答用户问题，资料中的表述、术语、数据和结论优先于你的自身知识；\n")
                .append("2. 资料与你已有的知识冲突时，一律以资料为准；\n")
                .append("3. 资料未覆盖的部分，明确说明\"资料中未提及\"，不要用自身知识冒充资料内容；如需补充，须注明是资料之外的通用知识。\n");
        return sb.toString();
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {}
}
