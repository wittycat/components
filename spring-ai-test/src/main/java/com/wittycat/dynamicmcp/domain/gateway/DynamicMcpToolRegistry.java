package com.wittycat.dynamicmcp.domain.gateway;

import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;

/**
 * 动态工具注册表网关（领域层定义能力，基础设施层提供 MCP 运行时实现）。
 *
 * <p>【DDD 网关/端口】"注册/注销/重同步/重名检查"是本上下文的业务动作，
 * 但落到哪个运行时（Spring AI 的 McpSyncServer）是技术细节——
 * infrastructure/mcp/DynamicMcpRegistrar 实现本接口并适配 SDK，
 * 应用层因此完全不 import 任何 MCP SDK 类型（端口与适配器模式）。</p>
 *
 * @author javachenxun
 * @date 2026/09/19
 */
public interface DynamicMcpToolRegistry {

    /** 注册或替换单个工具，并通知 MCP 客户端工具列表已变化。 */
    void register(DynamicMcpToolDO toolDO);

    /** 移除单个动态工具，并通知 MCP 客户端工具列表已变化。 */
    void unregister(String toolName);

    /** 清理旧动态工具后，按数据库已启用记录全量重建，返回同步数量。 */
    int resync();

    /** 校验工具名可用（不与静态 MCP 工具/已注册动态工具重名），冲突抛 TOOL_NAME_CONFLICT。 */
    void ensureNameAvailable(String toolName);
}
