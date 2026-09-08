package com.core.userinfo.interfaces.request;

/**
 * 新增请求
 */
public record CreateUserInfoRequest(
        String name,
        String description
) {
}
