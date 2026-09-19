package com.wittycat.dynamicmcp.infrastructure.mcp;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpErrorCode;
import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import com.wittycat.dynamicmcp.domain.gateway.DynamicMcpToolRegistry;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;
import com.wittycat.dynamicmcp.domain.repository.DynamicMcpToolRepository;
import com.wittycat.dynamicmcp.domain.service.DynamicMcpToolValidator;
import com.wittycat.dynamicmcp.infrastructure.executor.DynamicToolExecutor;

/**
 * 动态 MCP 工具注册表：实现领域层 {@link DynamicMcpToolRegistry} 网关，
 * 负责把数据库状态同步到 MCP 运行时（Spring AI 自动配置的 McpSyncServer）。
 *
 * <p>【DDD 基础设施层】MCP SDK 只允许出现在这里：DO → McpSchema.Tool 的组装、
 * addTool/removeTool + notifyToolsListChanged 的调用全部被本类封装，
 * 应用层与领域层对 SDK 零感知。另一条常见实现路线是每次请求实时查库的
 * 自研协议端点，这里换成 SDK 官方的动态工具 API，效果相同：tools/list 实时反映数据库。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
@Component
public class DynamicMcpRegistrar implements DynamicMcpToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicMcpRegistrar.class);

    private final McpSyncServer mcpSyncServer;
    private final DynamicMcpToolRepository repository;
    private final DynamicToolExecutor executor;
    private final DynamicMcpToolValidator validator;
    private final ObjectMapper objectMapper;
    private final ReentrantLock synchronizationLock = new ReentrantLock();
    private final Set<String> registeredDynamicToolNames = new HashSet<>();

    public DynamicMcpRegistrar(
            McpSyncServer mcpSyncServer,
            DynamicMcpToolRepository repository,
            DynamicToolExecutor executor,
            DynamicMcpToolValidator validator,
            ObjectMapper objectMapper) {
        this.mcpSyncServer = mcpSyncServer;
        this.repository = repository;
        this.executor = executor;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /** 应用就绪后从数据库恢复已启用动态工具。 */
    @EventListener(ApplicationReadyEvent.class)
    public void registerEnabledToolsOnStartup() {
        resync();
    }

    @Override
    public void register(DynamicMcpToolDO toolDO) {
        synchronizationLock.lock();
        try {
            ensureNameAvailable(toolDO.toolName());
            removeWithoutNotification(toolDO.toolName());
            mcpSyncServer.addTool(buildSpecification(toolDO));
            registeredDynamicToolNames.add(toolDO.toolName());
            mcpSyncServer.notifyToolsListChanged();
        } catch (RuntimeException exception) {
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_SYNC_FAILED,
                    "注册动态工具失败：" + toolDO.toolName(), exception);
        } finally {
            synchronizationLock.unlock();
        }
    }

    @Override
    public void unregister(String toolName) {
        synchronizationLock.lock();
        try {
            removeWithoutNotification(toolName);
            mcpSyncServer.notifyToolsListChanged();
        } catch (RuntimeException exception) {
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_SYNC_FAILED,
                    "移除动态工具失败：" + toolName, exception);
        } finally {
            synchronizationLock.unlock();
        }
    }

    @Override
    public int resync() {
        synchronizationLock.lock();
        try {
            Set<String> oldNames = new HashSet<>(registeredDynamicToolNames);
            oldNames.forEach(mcpSyncServer::removeTool);
            registeredDynamicToolNames.clear();
            List<DynamicMcpToolDO> enabledTools = repository.listEnabled();
            enabledTools.forEach(this::registerWithoutNotification);
            mcpSyncServer.notifyToolsListChanged();
            LOGGER.info("Dynamic MCP tools synchronized: count={}", enabledTools.size());
            return enabledTools.size();
        } catch (RuntimeException exception) {
            LOGGER.error("Dynamic MCP tools synchronization failed", exception);
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_SYNC_FAILED,
                    "动态工具重新同步失败", exception);
        } finally {
            synchronizationLock.unlock();
        }
    }

    @Override
    public void ensureNameAvailable(String toolName) {
        boolean ownedByDynamicTool = registeredDynamicToolNames.contains(toolName);
        boolean ownedByStaticTool = mcpSyncServer.listTools().stream()
                .anyMatch(tool -> tool.name().equals(toolName) && !ownedByDynamicTool);
        if (ownedByStaticTool) {
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_NAME_CONFLICT,
                    "工具名称与静态 MCP 工具冲突：" + toolName);
        }
    }

    private void registerWithoutNotification(DynamicMcpToolDO toolDO) {
        ensureNameAvailable(toolDO.toolName());
        mcpSyncServer.addTool(buildSpecification(toolDO));
        registeredDynamicToolNames.add(toolDO.toolName());
    }

    private void removeWithoutNotification(String toolName) {
        if (registeredDynamicToolNames.remove(toolName)) {
            mcpSyncServer.removeTool(toolName);
        }
    }

    private McpServerFeatures.SyncToolSpecification buildSpecification(DynamicMcpToolDO toolDO) {
        JsonNode schema = validator.readSchema(toolDO.inputSchema());
        Map<String, Object> properties = objectMapper.convertValue(schema.path("properties"), Map.class);
        List<String> required = schema.has("required")
                ? objectMapper.convertValue(schema.get("required"), List.class)
                : List.of();
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object", properties, required, Boolean.TRUE, Map.of(), Map.of());
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(toolDO.toolName())
                .description(toolDO.description())
                .inputSchema(inputSchema)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callTool(toolDO, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult callTool(DynamicMcpToolDO toolDO, Map<String, Object> arguments) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(executor.execute(toolDO, arguments))
                    .isError(false)
                    .build();
        } catch (DynamicMcpException exception) {
            LOGGER.error("Dynamic MCP tool execution failed: toolName={}, errorCode={}",
                    toolDO.toolName(), exception.getErrorCode().getCode(), exception);
            return McpSchema.CallToolResult.builder()
                    .addTextContent(exception.getMessage())
                    .isError(true)
                    .build();
        }
    }
}
