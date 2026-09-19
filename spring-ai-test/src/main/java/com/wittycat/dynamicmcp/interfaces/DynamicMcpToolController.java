package com.wittycat.dynamicmcp.interfaces;

import com.wittycat.dynamicmcp.application.DynamicMcpToolService;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 动态 MCP 工具管理接口（用户接口层：只做协议适配，不写业务）。
 *
 * <p>【DDD 接口层】Controller 的职责收窄为三件事：HTTP 路由、请求/响应 DTO 与
 * 领域对象互转、把动作委托给应用服务 {@link DynamicMcpToolService}。
 * 校验、落库、运行时同步都在应用层往下走，本类不 import 仓储和 MCP SDK。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
@RestController
@RequestMapping("/dynamic-mcp/tools")
public class DynamicMcpToolController {

    private final DynamicMcpToolService toolService;

    public DynamicMcpToolController(DynamicMcpToolService toolService) {
        this.toolService = toolService;
    }

    /** 查询工具列表。 */
    @GetMapping
    public List<ToolResponse> list() {
        return toolService.list().stream().map(ToolResponse::from).toList();
    }

    /** 创建禁用工具。 */
    @PostMapping
    public ResponseEntity<ToolResponse> create(@RequestBody ToolRequest request) {
        DynamicMcpToolDO created = toolService.create(request.toDO(null, null));
        return ResponseEntity.status(HttpStatus.CREATED).body(ToolResponse.from(created));
    }

    /** 更新工具定义并根据状态同步运行时注册表。 */
    @PutMapping("/{id}")
    public ToolResponse update(@PathVariable Long id, @RequestBody ToolRequest request) {
        return ToolResponse.from(toolService.update(id, request.toDO(id, null)));
    }

    /** 更新工具启停状态。 */
    @PutMapping("/{id}/status")
    public ToolResponse updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ToolResponse.from(toolService.changeStatus(id, request.status()));
    }

    /** 逻辑删除工具并移除运行时注册。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        toolService.delete(id);
    }

    /** 全量重建动态工具注册表。 */
    @PostMapping("/resync")
    public Map<String, Integer> resync() {
        return Map.of("count", toolService.resync());
    }

    /** 管理接口请求对象。 */
    public record ToolRequest(String toolName, String description, String toolType,
                              String httpMethod, String targetUrl, String inputSchema, String note) {
        DynamicMcpToolDO toDO(Long id, String status) {
            return new DynamicMcpToolDO(id, toolName, description, toolType, httpMethod,
                    targetUrl, inputSchema, status, note, null, null);
        }
    }

    /** 状态更新请求对象。 */
    public record StatusRequest(String status) {
    }

    /** 管理接口响应对象。 */
    public record ToolResponse(Long id, String toolName, String description, String toolType,
                               String httpMethod, String targetUrl, String inputSchema, String status,
                               String note, OffsetDateTime createTime, OffsetDateTime updateTime) {
        static ToolResponse from(DynamicMcpToolDO toolDO) {
            return new ToolResponse(toolDO.id(), toolDO.toolName(), toolDO.description(), toolDO.toolType(),
                    toolDO.httpMethod(), toolDO.targetUrl(), toolDO.inputSchema(), toolDO.status(),
                    toolDO.note(), toolDO.createTime(), toolDO.updateTime());
        }
    }
}
