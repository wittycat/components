package com.wittycat.dynamicmcp.domain.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpErrorCode;
import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;

/**
 * 动态工具字段及 JSON Schema 校验器（领域服务：业务规则的集中地）。
 *
 * <p>不依赖 Web/JDBC/MCP SDK，只依赖 Jackson——JSON 是本上下文的领域语言之一。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
@Component
public class DynamicMcpToolValidator {

    public static final String TYPE_HTTP = "HTTP";
    public static final String TYPE_ECHO = "ECHO";
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_SCHEMA_LENGTH = 32 * 1024;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([A-Za-z0-9_-]+)}");
    private static final Set<String> ALLOWED_TOOL_TYPES = Set.of(TYPE_HTTP, TYPE_ECHO);
    private static final Set<String> ALLOWED_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> ALLOWED_SCHEMA_TYPES = Set.of(
            "string", "integer", "number", "boolean", "array", "object");
    private static final Set<String> UNSUPPORTED_SCHEMA_KEYWORDS = Set.of(
            "$ref", "oneOf", "anyOf", "allOf");

    private final ObjectMapper objectMapper;

    public DynamicMcpToolValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 校验并规范化工具定义。 */
    public DynamicMcpToolDO validateAndNormalize(DynamicMcpToolDO toolDO) {
        String toolName = requireText(toolDO.toolName(), "工具名称不能为空");
        if (!TOOL_NAME_PATTERN.matcher(toolName).matches()) {
            throw invalid("工具名称只能包含字母、数字、下划线和连字符，长度为 1~64");
        }

        String description = requireText(toolDO.description(), "工具描述不能为空");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("工具描述不能超过 1000 个字符");
        }

        String toolType = requireText(toolDO.toolType(), "工具类型不能为空").toUpperCase();
        if (!ALLOWED_TOOL_TYPES.contains(toolType)) {
            throw invalid("工具类型仅支持 HTTP 或 ECHO");
        }

        String inputSchema = requireText(toolDO.inputSchema(), "输入 Schema 不能为空");
        validateSchema(inputSchema);

        String httpMethod = normalizeOptional(toolDO.httpMethod());
        String targetUrl = normalizeOptional(toolDO.targetUrl());
        if (TYPE_HTTP.equals(toolType)) {
            httpMethod = requireText(httpMethod, "HTTP 工具必须配置请求方法").toUpperCase();
            if (!ALLOWED_HTTP_METHODS.contains(httpMethod)) {
                throw invalid("HTTP 方法仅支持 GET、POST、PUT、PATCH、DELETE");
            }
            targetUrl = requireText(targetUrl, "HTTP 工具必须配置目标 URL");
            validateUrl(targetUrl);
        } else {
            httpMethod = null;
            targetUrl = null;
        }

        return new DynamicMcpToolDO(toolDO.id(), toolName, description, toolType, httpMethod,
                targetUrl, inputSchema, toolDO.status(), normalizeOptional(toolDO.note()),
                toolDO.createTime(), toolDO.updateTime());
    }

    /** 校验状态值。 */
    public String validateStatus(String status) {
        String normalizedStatus = requireText(status, "状态不能为空").toUpperCase();
        if (!Set.of(STATUS_ENABLED, STATUS_DISABLED).contains(normalizedStatus)) {
            throw invalid("状态仅支持 ENABLED 或 DISABLED");
        }
        return normalizedStatus;
    }

    /** 解析并校验执行参数的基础类型。 */
    public void validateArguments(String schemaText, Map<String, Object> arguments) {
        JsonNode schema = readSchema(schemaText);
        JsonNode properties = schema.path("properties");
        for (JsonNode requiredName : schema.path("required")) {
            String name = requiredName.asText();
            if (!arguments.containsKey(name) || arguments.get(name) == null) {
                throw invalid("缺少必填参数：" + name);
            }
        }
        arguments.forEach((name, value) -> validateArgumentType(name, value, properties.get(name)));
    }

    /** 解析已通过校验的 JSON Schema。 */
    public JsonNode readSchema(String schemaText) {
        try {
            return objectMapper.readTree(schemaText);
        } catch (JsonProcessingException exception) {
            throw invalid("输入 Schema 不是合法 JSON", exception);
        }
    }

    private void validateSchema(String schemaText) {
        if (schemaText.length() > MAX_SCHEMA_LENGTH) {
            throw invalid("输入 Schema 不能超过 32 KiB");
        }
        JsonNode schema = readSchema(schemaText);
        if (!schema.isObject() || !"object".equals(schema.path("type").asText())) {
            throw invalid("输入 Schema 根节点 type 必须为 object");
        }
        if (containsUnsupportedKeyword(schema)) {
            throw invalid("输入 Schema 不支持 $ref、oneOf、anyOf、allOf");
        }

        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw invalid("输入 Schema 的 properties 必须为对象");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode typeNode = field.getValue().get("type");
            if (typeNode == null || !ALLOWED_SCHEMA_TYPES.contains(typeNode.asText())) {
                throw invalid("参数 " + field.getKey() + " 必须声明受支持的 type");
            }
        }
        validateRequired(properties, schema.get("required"));
    }

    private static void validateRequired(JsonNode properties, JsonNode required) {
        if (required == null) {
            return;
        }
        if (!required.isArray()) {
            throw invalid("输入 Schema 的 required 必须为字符串数组");
        }
        for (JsonNode requiredName : required) {
            if (!requiredName.isTextual() || !properties.has(requiredName.asText())) {
                throw invalid("required 中的参数必须在 properties 中定义");
            }
        }
    }

    private static boolean containsUnsupportedKeyword(JsonNode node) {
        if (node.isObject()) {
            for (String keyword : UNSUPPORTED_SCHEMA_KEYWORDS) {
                if (node.has(keyword)) {
                    return true;
                }
            }
        }
        for (JsonNode child : node) {
            if (containsUnsupportedKeyword(child)) {
                return true;
            }
        }
        return false;
    }

    private static void validateArgumentType(String name, Object value, JsonNode propertySchema) {
        if (propertySchema == null) {
            throw invalid("存在 Schema 未定义的参数：" + name);
        }
        if (value == null) {
            return;
        }
        String type = propertySchema.path("type").asText();
        boolean valid = switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Iterable<?> || value.getClass().isArray();
            case "object" -> value instanceof Map<?, ?>;
            default -> false;
        };
        if (!valid) {
            throw invalid("参数 " + name + " 的类型应为 " + type);
        }
    }

    private static void validateUrl(String targetUrl) {
        if (targetUrl.length() > MAX_URL_LENGTH) {
            throw invalid("目标 URL 不能超过 2048 个字符");
        }
        try {
            String parseableUrl = PATH_PARAMETER_PATTERN.matcher(targetUrl)
                    .replaceAll("parameter-placeholder");
            URI uri = new URI(parseableUrl);
            if (!Set.of("http", "https").contains(uri.getScheme()) || !hasText(uri.getHost())) {
                throw invalid("目标 URL 必须是带主机名的 http/https 地址");
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null) {
                throw invalid("目标 URL 不能包含用户信息或 fragment");
            }
        } catch (URISyntaxException exception) {
            throw invalid("目标 URL 格式不合法", exception);
        }
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw invalid(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DynamicMcpException invalid(String message) {
        return new DynamicMcpException(DynamicMcpErrorCode.INVALID_PARAMETER, message);
    }

    private static DynamicMcpException invalid(String message, Throwable cause) {
        return new DynamicMcpException(DynamicMcpErrorCode.INVALID_PARAMETER, message, cause);
    }
}
