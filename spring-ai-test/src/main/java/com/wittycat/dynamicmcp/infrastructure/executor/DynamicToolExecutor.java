package com.wittycat.dynamicmcp.infrastructure.executor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpErrorCode;
import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import com.wittycat.dynamicmcp.domain.model.DynamicMcpToolDO;
import com.wittycat.dynamicmcp.domain.service.DynamicMcpToolValidator;

/**
 * 动态工具执行器（基础设施层）：支持本地回显和受限 HTTP 转发。
 *
 * <p>只允许访问 dynamic-mcp.allowed-hosts 白名单内的主机；请求/响应大小、
 * 连接/读超时、重定向与敏感 URL 成分均有硬限制。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
@Component
public class DynamicToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicToolExecutor.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10L);
    private static final int MAX_REQUEST_BODY_BYTES = 256 * 1024;
    private static final int MAX_RESPONSE_BODY_BYTES = 1024 * 1024;
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([A-Za-z0-9_-]+)}");

    private final ObjectMapper objectMapper;
    private final DynamicMcpToolValidator validator;
    private final HttpClient httpClient;
    private final Set<String> allowedHosts;

    public DynamicToolExecutor(
            ObjectMapper objectMapper,
            DynamicMcpToolValidator validator,
            @Value("${dynamic-mcp.allowed-hosts:127.0.0.1,localhost,::1}") Set<String> allowedHosts) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 执行工具并返回文本结果。
     *
     * @param toolDO 工具定义
     * @param arguments MCP 调用参数
     * @return 工具结果文本
     */
    public String execute(DynamicMcpToolDO toolDO, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        validator.validateArguments(toolDO.inputSchema(), safeArguments);
        if (DynamicMcpToolValidator.TYPE_ECHO.equals(toolDO.toolType())) {
            return writeJson(safeArguments);
        }
        if (DynamicMcpToolValidator.TYPE_HTTP.equals(toolDO.toolType())) {
            return executeHttp(toolDO, safeArguments);
        }
        throw new DynamicMcpException(DynamicMcpErrorCode.INVALID_PARAMETER,
                "不支持的工具类型：" + toolDO.toolType());
    }

    private String executeHttp(DynamicMcpToolDO toolDO, Map<String, Object> arguments) {
        long startTime = System.nanoTime();
        HttpTarget target = buildTarget(toolDO, arguments);
        HttpRequest request = buildRequest(toolDO.httpMethod(), target.uri(), target.bodyArguments());
        try {
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            String responseBody;
            try (InputStream inputStream = response.body()) {
                responseBody = readLimited(inputStream);
            }
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
            LOGGER.info("Dynamic MCP HTTP call completed: toolName={}, targetHost={}, statusCode={}, elapsedMs={}",
                    toolDO.toolName(), target.uri().getHost(), response.statusCode(), elapsedMillis);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_EXECUTION_FAILED,
                        "目标服务返回 HTTP " + response.statusCode() + "：" + responseBody);
            }
            return compactJsonOrText(responseBody);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_EXECUTION_FAILED,
                    "HTTP 工具调用被中断", exception);
        } catch (IOException exception) {
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_EXECUTION_FAILED,
                    "HTTP 工具调用失败", exception);
        }
    }

    private HttpTarget buildTarget(DynamicMcpToolDO toolDO, Map<String, Object> arguments) {
        Map<String, Object> remainingArguments = new LinkedHashMap<>(arguments);
        String targetUrl = replacePathParameters(toolDO.targetUrl(), remainingArguments);
        boolean queryMethod = "GET".equals(toolDO.httpMethod()) || "DELETE".equals(toolDO.httpMethod());
        if (queryMethod && !remainingArguments.isEmpty()) {
            targetUrl = appendQuery(targetUrl, remainingArguments);
            remainingArguments.clear();
        }

        URI uri = URI.create(targetUrl);
        validateAllowedTarget(uri);
        return new HttpTarget(uri, remainingArguments);
    }

    private HttpRequest buildRequest(String method, URI uri, Map<String, Object> bodyArguments) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, text/plain;q=0.9");
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }

        byte[] body = writeJson(bodyArguments).getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_REQUEST_BODY_BYTES) {
            throw new DynamicMcpException(DynamicMcpErrorCode.INVALID_PARAMETER,
                    "HTTP 请求体不能超过 256 KiB");
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    private void validateAllowedTarget(URI uri) {
        String host = uri.getHost();
        if (host == null || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new DynamicMcpException(DynamicMcpErrorCode.INVALID_PARAMETER,
                    "目标主机不在 dynamic-mcp.allowed-hosts 白名单中");
        }
    }

    private static String replacePathParameters(String url, Map<String, Object> remainingArguments) {
        Matcher matcher = PATH_PARAMETER_PATTERN.matcher(url);
        StringBuilder result = new StringBuilder(url.length());
        while (matcher.find()) {
            String parameterName = matcher.group(1);
            Object value = remainingArguments.remove(parameterName);
            if (value == null) {
                throw new DynamicMcpException(DynamicMcpErrorCode.INVALID_PARAMETER,
                        "URL 路径缺少参数：" + parameterName);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(encode(String.valueOf(value))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String appendQuery(String url, Map<String, Object> arguments) {
        List<String> pairs = new ArrayList<>(arguments.size());
        arguments.forEach((name, value) -> pairs.add(encode(name) + "=" + encode(String.valueOf(value))));
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + String.join("&", pairs);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String readLimited(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        int readBytes;
        while ((readBytes = inputStream.read(buffer)) != -1) {
            totalBytes += readBytes;
            if (totalBytes > MAX_RESPONSE_BODY_BYTES) {
                throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_EXECUTION_FAILED,
                        "目标服务响应体超过 1 MiB");
            }
            outputStream.write(buffer, 0, readBytes);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private String compactJsonOrText(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            return responseBody;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DynamicMcpException(DynamicMcpErrorCode.TOOL_EXECUTION_FAILED,
                    "工具参数序列化失败", exception);
        }
    }

    private record HttpTarget(URI uri, Map<String, Object> bodyArguments) {
    }
}
