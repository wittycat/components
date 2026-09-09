package com.wittycat.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    private int memoryMaxMessages = 20;
    /** RAG 检索返回的分块数。详解类问题常跨多个分块，过小会导致资料覆盖不全、模型用自身知识补齐 */
    private int ragTopK = 5;
    /** 向量检索相似度阈值，低于该值视为不相关，降级为关键词搜索 */
    private double ragSimilarityThreshold = 0.58;
    /** 整篇文档注入预算（字符）：>0 时命中文档全文不超过该值才整篇注入；<=0 不限制，命中文档一律整篇注入，杜绝跨块截断 */
    private int ragWholeDocMaxChars = 0;
    private int maxToolIterations = 5;
    private String workspaceDir;
    /** 上传文件最大大小（KB），<=0 表示不限制 */
    private int maxUploadSizeKb = 0;
    /** 单个文档最大分块数（低于实际分块数会静默截断文档，后文内容将无法被检索到） */
    private int maxChunksPerDocument = 100000;
    /** 向量检索最多加载的分块数（超过则整体降级为关键词检索） */
    private int maxVectorSearchChunks = 4000;
    /** 每个文档最多生成 embedding 的分块数（超出部分无向量，只能被关键词检索命中） */
    private int maxEmbeddingsPerDocument = 100000;
}
