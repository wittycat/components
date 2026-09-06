package com.agent.rag;

import com.agent.config.AgentConfig;
import com.agent.dto.KnowledgeDocumentDto;
import com.agent.entity.KnowledgeChunk;
import com.agent.entity.KnowledgeDocument;
import com.agent.repository.KnowledgeChunkRepository;
import com.agent.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库文档持久化服务（独立事务）
 * 将文档/分块的保存逻辑从 DocumentIngestionService 中分离，
 * 确保 @Transactional 代理能正确拦截（避免 Spring 自调用失效问题）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentStore {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final TextChunker textChunker;
    private final AgentConfig agentConfig;

    /**
     * 在独立事务中保存文档和分块（不含 embedding）
     */
    @Transactional
    public KnowledgeDocument saveDocumentAndChunks(String filename, String content, String contentHash) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFilename(filename);
        doc.setContent(content.length() > 200 ? content.substring(0, 200) + "..." : content);
        doc.setContentHash(contentHash);
        doc = documentRepository.save(doc);
        log.info("[Store] 文档记录已保存, docId={}", doc.getId());

        List<String> chunks = textChunker.chunk(content);
        int maxChunks = agentConfig.getMaxChunksPerDocument();
        if (chunks.size() > maxChunks) {
            chunks = chunks.subList(0, maxChunks);
            log.warn("[Store] 文档 '{}' 分块数超出限制，截断至 {} 个", filename, maxChunks);
        }

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunkRepository.save(chunk);
        }
        log.info("[Store] 分块已保存, docId={}, 分块数={}", doc.getId(), chunks.size());

        return doc;
    }

    /**
     * 查询所有文档（含分块数统计）
     */
    public List<KnowledgeDocumentDto> listDocuments() {
        // 批量查询分块数，避免 N+1 问题
        Map<Long, Long> chunkCounts = chunkRepository.countGroupByDocumentId().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return documentRepository.findAll().stream()
                .map(doc -> KnowledgeDocumentDto.builder()
                        .id(doc.getId())
                        .filename(doc.getFilename())
                        .createdAt(doc.getCreatedAt())
                        .chunkCount(chunkCounts.getOrDefault(doc.getId(), 0L).intValue())
                        .build())
                .toList();
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
        log.info("[Store] 文档删除成功, docId={}", id);
    }
}
