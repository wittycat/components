package com.wittycat.dynamicmcp.interfaces;

import com.wittycat.dynamicmcp.domain.exception.DynamicMcpErrorCode;
import com.wittycat.dynamicmcp.domain.exception.DynamicMcpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 动态 MCP 管理接口统一异常响应（接口层：业务错误码 → HTTP 协议语义的映射点）。
 *
 * <p>【DDD 分层】领域层 ErrorCode 只有业务语义；"参数错回 400、冲突回 409、
 * 下游挂了回 502"这类 HTTP 决策集中在本类。唯一键冲突已由基础设施层翻译成
 * TOOL_NAME_CONFLICT，所以这里只需处理领域异常一种。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
@RestControllerAdvice(basePackageClasses = DynamicMcpToolController.class)
public class DynamicMcpExceptionHandler {

    /** 将业务异常转换为包含错误码、错误消息和用户提示的响应。 */
    @ExceptionHandler(DynamicMcpException.class)
    public ResponseEntity<Map<String, String>> handleDynamicMcpException(DynamicMcpException exception) {
        DynamicMcpErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(httpStatusOf(errorCode)).body(Map.of(
                "errorCode", errorCode.getCode(),
                "errorMessage", exception.getMessage(),
                "userTip", errorCode.getUserTip()));
    }

    private static HttpStatus httpStatusOf(DynamicMcpErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_PARAMETER -> HttpStatus.BAD_REQUEST;
            case TOOL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TOOL_NAME_CONFLICT -> HttpStatus.CONFLICT;
            case TOOL_SYNC_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case TOOL_EXECUTION_FAILED -> HttpStatus.BAD_GATEWAY;
        };
    }
}
