package com.agent.rag;

import com.agent.config.AgentConfig;
import com.agent.dto.KnowledgeDocumentDto;
import com.agent.entity.KnowledgeChunk;
import com.agent.entity.KnowledgeDocument;
import com.agent.repository.KnowledgeChunkRepository;
import com.agent.repository.KnowledgeDocumentRepository;
import com.agent.service.GlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final KnowledgeDocumentStore documentStore;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final TextChunker textChunker;
    private final GlmClient glmClient;
    private final ObjectMapper objectMapper;
    private final AgentConfig agentConfig;

    /** 异步生成 embedding 的线程池 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        log.info("[Ingestion] 线程池已关闭");
    }

    /**
     * 导入文本：先保存文档和分块（事务内），再异步生成 embedding（事务外）
     */
    public KnowledgeDocument ingestText(String filename, String content) {
        log.info("[Ingestion] 开始导入文本, filename={}, content长度={}", filename, content != null ? content.length() : 0);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }

        // 同名同内容查重：重复上传（双击/重试）会成倍放大分块与 embedding 调用，且重复分块挤占检索 top-K
        if (filename != null) {
            String contentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
            Optional<KnowledgeDocument> existing = documentRepository.findFirstByFilenameAndContentHash(filename, contentHash);
            if (existing.isPresent()) {
                log.warn("[Ingestion] 重复文档，跳过入库, filename={}, 已有docId={}", filename, existing.get().getId());
                return existing.get();
            }
            // 1. 事务内：保存文档和分块（不含 embedding）
            return ingest(filename, content, contentHash);
        }
        return ingest(filename, content, null);
    }

    private KnowledgeDocument ingest(String filename, String content, String contentHash) {
        KnowledgeDocument doc = documentStore.saveDocumentAndChunks(filename, content, contentHash);

        // 2. 事务外：异步生成 embedding（失败不影响文档保存）
        generateEmbeddingsAsync(doc.getId(), content);

        log.info("[Ingestion] 文档导入完成, docId={}, filename={}", doc.getId(), filename);
        return doc;
    }

    /**
     * 异步生成 embedding：失败不影响文档和分块的保存
     */
    private void generateEmbeddingsAsync(Long docId, String content) {
        executor.execute(() -> {
            try {
                log.info("[Ingestion] 开始异步生成 embedding, docId={}", docId);
                List<String> chunks = textChunker.chunk(content);
                int maxChunks = agentConfig.getMaxChunksPerDocument();
                if (chunks.size() > maxChunks) {
                    chunks = chunks.subList(0, maxChunks);
                }

                int maxEmbeddings = agentConfig.getMaxEmbeddingsPerDocument();
                int embeddingSuccessCount = 0;
                List<KnowledgeChunk> dbChunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId);

                for (int i = 0; i < Math.min(chunks.size(), dbChunks.size()); i++) {
                    if (i >= maxEmbeddings){
                        break;
                    }
                    try {
                        log.debug("[Ingestion] 为第 {} 个分块生成 embedding", i);
                        List<Double> embedding = glmClient.getEmbedding(chunks.get(i));
                        if (embedding != null) {
                            KnowledgeChunk chunk = dbChunks.get(i);
                            chunk.setEmbedding(objectMapper.writeValueAsString(embedding));
                            chunkRepository.save(chunk);
                            embeddingSuccessCount++;
                        }
                    } catch (Exception e) {
                        log.warn("[Ingestion] 第 {} 个分块 embedding 生成失败: {}", i, e.getMessage());
                    }
                }
                log.info("[Ingestion] Embedding 生成完成, docId={}, 成功数={}/{}", docId, embeddingSuccessCount, Math.min(chunks.size(), maxEmbeddings));
            } catch (Exception e) {
                log.error("[Ingestion] 异步生成 embedding 失败, docId={}", docId, e);
            }
        });
    }

    public KnowledgeDocument ingestFile(MultipartFile file) throws Exception {
        log.info("[Ingestion] 开始上传文件, filename={}, size={} bytes", 
                file.getOriginalFilename(), file.getSize());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        long maxBytes = (long) agentConfig.getMaxUploadSizeKb() * 1024;
        if (file.getSize() > maxBytes) {
            log.warn("[Ingestion] 文件过大, 文件大小={}, 最大允许={} KB", file.getSize(), agentConfig.getMaxUploadSizeKb());
            throw new IllegalArgumentException(
                    "文件过大，最大允许 " + agentConfig.getMaxUploadSizeKb() + " KB");
        }

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "untitled.txt";
        }
        log.info("[Ingestion] 文件读取成功, filename={}, content长度={}", filename, content.length());
        return ingestText(filename, content);
    }

    public List<KnowledgeDocumentDto> listDocuments() {
        log.info("[Ingestion] 查询知识库文档列表");
        List<KnowledgeDocumentDto> docs = documentStore.listDocuments();
        log.info("[Ingestion] 查询到 {} 个文档", docs.size());
        return docs;
    }

    @Transactional
    public void deleteDocument(Long id) {
        log.info("[Ingestion] 删除文档, docId={}", id);
        chunkRepository.deleteByDocumentId(id);
        documentStore.deleteDocument(id);
        log.info("[Ingestion] 文档删除成功, docId={}", id);
    }
}
