-- 可选：手动初始化数据库（JPA ddl-auto=update 会自动建表）
CREATE DATABASE IF NOT EXISTS ai_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_agent;

-- 对话会话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL DEFAULT '新对话',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL COMMENT 'user/assistant/system/tool',
    content         TEXT,
    tool_calls      JSON COMMENT '工具调用信息',
    tool_call_id    VARCHAR(100) COMMENT '工具响应关联ID',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename    VARCHAR(255) NOT NULL,
    content     LONGTEXT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库文档块表（RAG 检索单元）
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   JSON COMMENT '向量嵌入（可选）',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document (document_id),
    -- ngram 分词器：MySQL 默认按空格/标点分词，中文整句会被当成一个词导致全文检索失效
    FULLTEXT INDEX ft_content (content) WITH PARSER ngram,
    FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


