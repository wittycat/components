package com.wittycat.knowledgebase.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时补齐缺失 embedding 的分块：保证任何已上传文档全量可向量检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private final DocumentIngestionService ingestionService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int fixed = ingestionService.backfillMissingEmbeddings();
            if (fixed > 0) {
                log.info("[Ingestion] 启动补齐 embedding 完成, 共 {} 块", fixed);
            }
        } catch (Exception e) {
            // 补齐失败不影响应用启动（下次启动会再次尝试）
            log.error("[Ingestion] 启动补齐 embedding 失败", e);
        }
    }
}
