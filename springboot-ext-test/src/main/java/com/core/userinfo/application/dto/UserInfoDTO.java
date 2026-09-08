package com.core.userinfo.application.dto;

import java.time.LocalDateTime;

/**
 * 用户信息表 - 数据传输对象
 */
public record UserInfoDTO(
        Long id,
        String name,
        String description,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
