package com.wittycat.dynamicmcp.domain.repository;

import java.util.List;
import java.util.Optional;

import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;

/**
 * 动态 MCP 工具仓储接口（领域层定义"要什么"，基础设施层决定"怎么拿"）。
 *
 * <p>【DDD 依赖倒置】应用层/领域层只面向本接口编程；
 * JdbcClient + PostgreSQL 的实现在 infrastructure/repository，换存储只动实现类。</p>
 *
 * @author javachenxun
 * @date 2026/09/19
 */
public interface DynamicMcpToolRepository {

    /** 查询全部未删除工具。 */
    List<DynamicMcpToolDO> listAll();

    /** 查询全部已启用且未删除工具。 */
    List<DynamicMcpToolDO> listEnabled();

    /** 按主键查询未删除工具。 */
    Optional<DynamicMcpToolDO> getById(Long id);

    /** 新增工具并返回数据库生成的完整记录；名称冲突抛 TOOL_NAME_CONFLICT。 */
    DynamicMcpToolDO insert(DynamicMcpToolDO toolDO);

    /** 更新工具定义（状态由 {@link #updateStatus} 单独维护），返回更新后的记录。 */
    DynamicMcpToolDO update(Long id, DynamicMcpToolDO toolDO);

    /** 更新工具启停状态，返回更新后的记录。 */
    DynamicMcpToolDO updateStatus(Long id, String status);

    /** 逻辑删除工具。 */
    void delete(Long id);
}
