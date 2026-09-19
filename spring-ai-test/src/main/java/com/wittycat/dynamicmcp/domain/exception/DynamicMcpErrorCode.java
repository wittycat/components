package com.wittycat.dynamicmcp.domain.exception;

/**
 * 动态 MCP 管理接口错误码（领域层：只描述业务语义）。
 *
 * <p>【DDD 分层】刻意不携带 HTTP 状态码——"冲突该回 409 还是 500"是接口协议的事，
 * 由 interfaces 层的 ExceptionHandler 负责映射，避免领域层反向依赖 Spring Web。</p>
 *
 * @author javachenxun
 * @date 2026/09/17
 */
public enum DynamicMcpErrorCode {

    /** 请求参数不合法。 */
    INVALID_PARAMETER("A0001", "请检查工具配置"),
    /** 请求的工具不存在。 */
    TOOL_NOT_FOUND("A0002", "工具不存在或已删除"),
    /** 工具名称与已有工具冲突。 */
    TOOL_NAME_CONFLICT("A0003", "工具名称已被使用"),
    /** 动态工具同步失败。 */
    TOOL_SYNC_FAILED("B0001", "工具同步失败，请执行重新同步"),
    /** 动态工具执行失败。 */
    TOOL_EXECUTION_FAILED("C0001", "目标服务调用失败");

    private final String code;
    private final String userTip;

    DynamicMcpErrorCode(String code, String userTip) {
        this.code = code;
        this.userTip = userTip;
    }

    public String getCode() {
        return code;
    }

    public String getUserTip() {
        return userTip;
    }
}
