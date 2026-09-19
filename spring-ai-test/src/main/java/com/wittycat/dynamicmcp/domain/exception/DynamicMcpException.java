package com.wittycat.dynamicmcp.domain.exception;

import java.util.Objects;

/**
 * 动态 MCP 业务异常（领域层异常：只有业务语义，HTTP 状态码由接口层决定）。
 *
 * @author javachenxun
 * @date 2026/09/17
 */
public class DynamicMcpException extends RuntimeException {

    private final DynamicMcpErrorCode errorCode;

    public DynamicMcpException(DynamicMcpErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode 不能为空");
    }

    public DynamicMcpException(DynamicMcpErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode 不能为空");
    }

    public DynamicMcpErrorCode getErrorCode() {
        return errorCode;
    }
}
