package com.core.userinfo.interfaces.request;

/**
 * 修改请求
 */
public record UpdateUserInfoRequest(
        String name,
        String description
) {
}
