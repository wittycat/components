package com.wittycat.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    private int memoryMaxMessages = 20;
    private int ragTopK = 3;
    /** 向量检索相似度阈值，低于该值视为不相关，降级为关键词搜索 */
    private double ragSimilarityThreshold = 0.58;
    private int maxToolIterations = 5;
    private String workspaceDir;
    /** 上传文件最大大小（KB） */
    private int maxUploadSizeKb = 2048;
    /** 单个文档最大分块数（低于该值会截断文档，后文内容将无法被检索到） */
    private int maxChunksPerDocument = 300;
    /** 向量检索最多加载的分块数（超过则整体降级为关键词检索） */
    private int maxVectorSearchChunks = 400;
    /** 每个文档最多生成 embedding 的分块数（超出部分无向量，只能被关键词检索命中） */
    private int maxEmbeddingsPerDocument = 300;
}
