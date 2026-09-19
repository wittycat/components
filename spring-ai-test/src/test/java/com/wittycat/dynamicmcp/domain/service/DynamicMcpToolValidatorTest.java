package com.wittycat.dynamicmcp.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;

/**
 * 动态工具参数校验单元测试。
 *
 * @author javachenxun
 * @date 2026/09/17
 */
class DynamicMcpToolValidatorTest {

    private DynamicMcpToolValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DynamicMcpToolValidator(new ObjectMapper());
    }

    @Test
    void shouldValidateEchoSchemaAndArguments() {
        DynamicMcpToolDO toolDO = validator.validateAndNormalize(new DynamicMcpToolDO(
                null, "echo_args", "回显", "echo", null, null,
                "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}",
                "DISABLED", null, null, null));

        validator.validateArguments(toolDO.inputSchema(), Map.of("message", "hello"));
        assertThat(toolDO.toolType()).isEqualTo("ECHO");
    }

    @Test
    void shouldRejectMissingRequiredArgument() {
        String schema = "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}";

        assertThatThrownBy(() -> validator.validateArguments(schema, Map.of()))
                .isInstanceOf(DynamicMcpException.class)
                .hasMessageContaining("缺少必填参数");
    }

    @Test
    void shouldRejectUnsafeTargetUrl() {
        DynamicMcpToolDO toolDO = new DynamicMcpToolDO(
                null, "remote", "远程", "HTTP", "GET", "http://user:password@example.com/api",
                "{\"type\":\"object\",\"properties\":{}}", "DISABLED", null, null, null);

        assertThatThrownBy(() -> validator.validateAndNormalize(toolDO))
                .isInstanceOf(DynamicMcpException.class)
                .hasMessageContaining("用户信息");
    }

    @Test
    void shouldAcceptPathPlaceholderInHttpUrl() {
        DynamicMcpToolDO toolDO = new DynamicMcpToolDO(
                null, "lookup_item", "查询", "HTTP", "GET",
                "http://127.0.0.1:8084/items/{itemId}",
                "{\"type\":\"object\",\"properties\":{\"itemId\":{\"type\":\"string\"}},\"required\":[\"itemId\"]}",
                "DISABLED", null, null, null);

        assertThat(validator.validateAndNormalize(toolDO).targetUrl())
                .isEqualTo("http://127.0.0.1:8084/items/{itemId}");
    }
}
