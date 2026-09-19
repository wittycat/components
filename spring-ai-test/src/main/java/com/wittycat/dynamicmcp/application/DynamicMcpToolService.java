package com.wittycat.dynamicmcp.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpErrorCode;
import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import com.wittycat.dynamicmcp.domain.gateway.DynamicMcpToolRegistry;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;
import com.wittycat.dynamicmcp.domain.repository.DynamicMcpToolRepository;
import com.wittycat.dynamicmcp.domain.service.DynamicMcpToolValidator;

/**
 * 动态 MCP 工具应用服务：管理页每个动作对应一个用例（校验 → 落库 → 按状态同步运行时）。
 *
 * <p>【DDD 应用层】只做编排：业务规则在 domain（Validator），协议与存储在
 * infrastructure（Registrar 适配 MCP SDK、Repository 实现走 PG）。本类不 import
 * 任何 Controller DTO、JDBC 或 MCP SDK 类型，接口层换协议、存储换库都不影响这里。</p>
 *
 * @author javachenxun
 * @date 2026/09/19
 */
@Service
public class DynamicMcpToolService {

    private final DynamicMcpToolRepository repository;
    private final DynamicMcpToolRegistry registry;
    private final DynamicMcpToolValidator validator;

    public DynamicMcpToolService(
            DynamicMcpToolRepository repository,
            DynamicMcpToolRegistry registry,
            DynamicMcpToolValidator validator) {
        this.repository = repository;
        this.registry = registry;
        this.validator = validator;
    }

    /** 查询工具列表。 */
    public List<DynamicMcpToolDO> list() {
        return repository.listAll();
    }

    /** 创建禁用工具：先校验并占用名称，再落库（新建一律 DISABLED，启用走独立动作）。 */
    public DynamicMcpToolDO create(DynamicMcpToolDO draft) {
        DynamicMcpToolDO normalized = new DynamicMcpToolDO(null, draft.toolName(), draft.description(),
                draft.toolType(), draft.httpMethod(), draft.targetUrl(), draft.inputSchema(),
                DynamicMcpToolValidator.STATUS_DISABLED, draft.note(), null, null);
        DynamicMcpToolDO validated = validator.validateAndNormalize(normalized);
        registry.ensureNameAvailable(validated.toolName());
        return repository.insert(validated);
    }

    /** 更新工具定义，已启用工具的定义变化实时反映到运行时（改名则先注销旧名）。 */
    public DynamicMcpToolDO update(Long id, DynamicMcpToolDO draft) {
        DynamicMcpToolDO current = requireTool(id);
        DynamicMcpToolDO normalized = new DynamicMcpToolDO(id, draft.toolName(), draft.description(),
                draft.toolType(), draft.httpMethod(), draft.targetUrl(), draft.inputSchema(),
                current.status(), draft.note(), null, null);
        DynamicMcpToolDO validated = validator.validateAndNormalize(normalized);
        registry.ensureNameAvailable(validated.toolName());
        DynamicMcpToolDO updated = repository.update(id, validated);
        if (DynamicMcpToolValidator.STATUS_ENABLED.equals(updated.status())) {
            if (!current.toolName().equals(updated.toolName())) {
                registry.unregister(current.toolName());
            }
            registry.register(updated);
        } else {
            registry.unregister(current.toolName());
            registry.unregister(updated.toolName());
        }
        return updated;
    }

    /** 更新工具启停状态：启用即注册进 MCP 运行时，停用即移除。 */
    public DynamicMcpToolDO changeStatus(Long id, String status) {
        String validatedStatus = validator.validateStatus(status);
        DynamicMcpToolDO updated = repository.updateStatus(id, validatedStatus);
        if (DynamicMcpToolValidator.STATUS_ENABLED.equals(validatedStatus)) {
            registry.register(updated);
        } else {
            registry.unregister(updated.toolName());
        }
        return updated;
    }

    /** 逻辑删除工具并移除运行时注册。 */
    public void delete(Long id) {
        DynamicMcpToolDO current = requireTool(id);
        repository.delete(id);
        registry.unregister(current.toolName());
    }

    /** 全量重建动态工具注册表，返回同步数量。 */
    public int resync() {
        return registry.resync();
    }

    private DynamicMcpToolDO requireTool(Long id) {
        return repository.getById(id).orElseThrow(() -> new DynamicMcpException(
                DynamicMcpErrorCode.TOOL_NOT_FOUND, "工具不存在，id=" + id));
    }
}
