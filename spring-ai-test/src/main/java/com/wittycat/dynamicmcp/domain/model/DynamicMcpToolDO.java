package com.wittycat.dynamicmcp.domain.model;

import java.time.OffsetDateTime;

/**
 * 动态 MCP 工具数据对象，与 springai_mcp_tool_registration 表一一对应。
 *
 * <p>领域层模型：只承载业务字段，不掺入任何持久化/传输框架细节。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
public record DynamicMcpToolDO(
        Long id,
        String toolName,
        String description,
        String toolType,
        String httpMethod,
        String targetUrl,
        String inputSchema,
        String status,
        String note,
        OffsetDateTime createTime,
        OffsetDateTime updateTime) {
}
