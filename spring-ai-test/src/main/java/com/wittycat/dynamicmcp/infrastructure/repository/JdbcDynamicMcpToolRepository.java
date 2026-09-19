package com.wittycat.dynamicmcp.infrastructure.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpErrorCode;
import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;
import com.wittycat.dynamicmcp.domain.repository.DynamicMcpToolRepository;

/**
 * 动态 MCP 工具仓储的 JDBC 实现（基础设施层：domain 接口的落地）。
 *
 * <p>所有外部值均通过 JDBC 参数绑定写入，JSON Schema 在 PostgreSQL 侧转换成 jsonb；
 * 唯一键冲突（DuplicateKeyException）在本层翻译成领域异常 TOOL_NAME_CONFLICT，
 * 上层因此不需要知道 Spring DAO 的异常体系。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
@Repository
public class JdbcDynamicMcpToolRepository implements DynamicMcpToolRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, tool_name, description, tool_type, http_method, target_url,
                   input_schema::text, status, note, create_time, update_time
              FROM springai_mcp_tool_registration
            """;

    private static final RowMapper<DynamicMcpToolDO> ROW_MAPPER = JdbcDynamicMcpToolRepository::mapRow;

    private final JdbcClient jdbcClient;

    public JdbcDynamicMcpToolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcClient = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public List<DynamicMcpToolDO> listAll() {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE is_deleted = 0 ORDER BY id")
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public List<DynamicMcpToolDO> listEnabled() {
        return jdbcClient.sql(SELECT_COLUMNS
                        + " WHERE status = 'ENABLED' AND is_deleted = 0 ORDER BY id")
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public Optional<DynamicMcpToolDO> getById(Long id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id AND is_deleted = 0")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public DynamicMcpToolDO insert(DynamicMcpToolDO toolDO) {
        try {
            Long id = jdbcClient.sql("""
                            INSERT INTO springai_mcp_tool_registration
                                (tool_name, description, tool_type, http_method, target_url,
                                 input_schema, status, note)
                            VALUES (:toolName, :description, :toolType, :httpMethod, :targetUrl,
                                CAST(:inputSchema AS jsonb), :status, :note)
                            RETURNING id
                            """)
                    .param("toolName", toolDO.toolName())
                    .param("description", toolDO.description())
                    .param("toolType", toolDO.toolType())
                    .param("httpMethod", toolDO.httpMethod(), Types.VARCHAR)
                    .param("targetUrl", toolDO.targetUrl(), Types.VARCHAR)
                    .param("inputSchema", toolDO.inputSchema())
                    .param("status", toolDO.status())
                    .param("note", toolDO.note(), Types.VARCHAR)
                    .query(Long.class)
                    .single();
            return getRequiredById(id);
        } catch (DuplicateKeyException exception) {
            throw nameConflict(toolDO.toolName(), exception);
        }
    }

    @Override
    public DynamicMcpToolDO update(Long id, DynamicMcpToolDO toolDO) {
        try {
            int affectedRows = jdbcClient.sql("""
                            UPDATE springai_mcp_tool_registration
                               SET tool_name = :toolName,
                                   description = :description,
                                   tool_type = :toolType,
                                   http_method = :httpMethod,
                                   target_url = :targetUrl,
                                   input_schema = CAST(:inputSchema AS jsonb),
                                   note = :note,
                                   update_time = CURRENT_TIMESTAMP
                             WHERE id = :id AND is_deleted = 0
                            """)
                    .param("toolName", toolDO.toolName())
                    .param("description", toolDO.description())
                    .param("toolType", toolDO.toolType())
                    .param("httpMethod", toolDO.httpMethod(), Types.VARCHAR)
                    .param("targetUrl", toolDO.targetUrl(), Types.VARCHAR)
                    .param("inputSchema", toolDO.inputSchema())
                    .param("note", toolDO.note(), Types.VARCHAR)
                    .param("id", id)
                    .update();
            requireAffectedRow(affectedRows, id);
            return getRequiredById(id);
        } catch (DuplicateKeyException exception) {
            throw nameConflict(toolDO.toolName(), exception);
        }
    }

    @Override
    public DynamicMcpToolDO updateStatus(Long id, String status) {
        int affectedRows = jdbcClient.sql("""
                        UPDATE springai_mcp_tool_registration
                           SET status = :status, update_time = CURRENT_TIMESTAMP
                         WHERE id = :id AND is_deleted = 0
                        """)
                .param("status", status)
                .param("id", id)
                .update();
        requireAffectedRow(affectedRows, id);
        return getRequiredById(id);
    }

    @Override
    public void delete(Long id) {
        int affectedRows = jdbcClient.sql("""
                        UPDATE springai_mcp_tool_registration
                           SET status = 'DISABLED', is_deleted = 1, update_time = CURRENT_TIMESTAMP
                         WHERE id = :id AND is_deleted = 0
                        """)
                .param("id", id)
                .update();
        requireAffectedRow(affectedRows, id);
    }

    private static DynamicMcpException nameConflict(String toolName, DuplicateKeyException cause) {
        return new DynamicMcpException(DynamicMcpErrorCode.TOOL_NAME_CONFLICT,
                "工具名称已被使用：" + toolName, cause);
    }

    private DynamicMcpToolDO getRequiredById(Long id) {
        return getById(id).orElseThrow(() -> new DynamicMcpException(
                DynamicMcpErrorCode.TOOL_NOT_FOUND, "工具不存在，id=" + id));
    }

    private static void requireAffectedRow(int affectedRows, Long id) {
        if (affectedRows == 0) {
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_NOT_FOUND,
                    "工具不存在，id=" + id);
        }
    }

    private static DynamicMcpToolDO mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DynamicMcpToolDO(
                resultSet.getLong("id"), resultSet.getString("tool_name"),
                resultSet.getString("description"), resultSet.getString("tool_type"),
                resultSet.getString("http_method"), resultSet.getString("target_url"),
                resultSet.getString("input_schema"), resultSet.getString("status"),
                resultSet.getString("note"),
                resultSet.getObject("create_time", OffsetDateTime.class),
                resultSet.getObject("update_time", OffsetDateTime.class));
    }
}
