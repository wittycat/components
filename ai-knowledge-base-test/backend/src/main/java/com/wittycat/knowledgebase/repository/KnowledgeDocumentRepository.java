package com.wittycat.knowledgebase.repository;

import com.wittycat.knowledgebase.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Optional<KnowledgeDocument> findFirstByFilenameAndContentHash(String filename, String contentHash);
}
