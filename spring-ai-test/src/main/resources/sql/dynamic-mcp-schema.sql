CREATE TABLE IF NOT EXISTS springai_mcp_tool_registration (
    id          BIGSERIAL PRIMARY KEY,
    tool_name   VARCHAR(64) NOT NULL,
    description TEXT         NOT NULL,
    tool_type   VARCHAR(16) NOT NULL DEFAULT 'HTTP',
    http_method VARCHAR(8),
    target_url  TEXT,
    input_schema JSONB       NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    note        TEXT,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_springai_mcp_tool_registration_tool_name UNIQUE (tool_name)
);

ALTER TABLE springai_mcp_tool_registration
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE springai_mcp_tool_registration
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE springai_mcp_tool_registration
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

INSERT INTO springai_mcp_tool_registration (
    tool_name,
    description,
    tool_type,
    input_schema,
    status
)
VALUES (
    'echo_args',
    '回显参数，验证动态 MCP 注册',
    'ECHO',
    '{"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}'::jsonb,
    'ENABLED'
)
ON CONFLICT (tool_name) DO NOTHING;

INSERT INTO springai_mcp_tool_registration (
    tool_name,
    description,
    tool_type,
    http_method,
    target_url,
    input_schema,
    status
)
VALUES (
    'rag_kb_search',
    '调用本模块 RAG 检索',
    'HTTP',
    'GET',
    'http://127.0.0.1:8084/rag/search',
    '{"type":"object","properties":{"q":{"type":"string"}},"required":["q"]}'::jsonb,
    'ENABLED'
)
ON CONFLICT (tool_name) DO NOTHING;
