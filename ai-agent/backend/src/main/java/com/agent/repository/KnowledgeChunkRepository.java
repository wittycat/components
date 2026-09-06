package com.agent.repository;

import com.agent.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    void deleteByDocumentId(Long documentId);

    long count();

    @Query(value = "SELECT * FROM knowledge_chunk WHERE embedding IS NOT NULL ORDER BY id LIMIT ?1",
           nativeQuery = true)
    List<KnowledgeChunk> findChunksWithEmbedding(int limit);

    // 解释：该 SQL 使用 MySQL 全文索引（Full-Text Index）进行自然语言模式的关键词检索。
    // MATCH(content) 指定对 content 列进行全文匹配；
    // AGAINST(?1 IN NATURAL LANGUAGE MODE) 表示以自然语言模式搜索参数 ?1，
    // 相比 LIKE 查询，全文索引支持分词且查询效率更高；
    // LIMIT ?2 用于限制返回的结果数量。
    @Query(value = "SELECT * FROM knowledge_chunk WHERE MATCH(content) AGAINST(?1 IN NATURAL LANGUAGE MODE) LIMIT ?2",
           nativeQuery = true)
    List<KnowledgeChunk> searchByKeyword(String keyword, int limit);

    @Query(value = "SELECT * FROM knowledge_chunk WHERE content LIKE CONCAT('%', ?1, '%') LIMIT ?2",
           nativeQuery = true)
    List<KnowledgeChunk> searchByLike(String keyword, int limit);

    long countByDocumentId(Long documentId);

    @Query(value = "SELECT document_id AS docId, COUNT(*) AS cnt FROM knowledge_chunk GROUP BY document_id",
           nativeQuery = true)
    List<Object[]> countGroupByDocumentId();
}
