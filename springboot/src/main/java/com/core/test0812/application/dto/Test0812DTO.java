package com.core.test0812.application.dto;

import java.time.LocalDateTime;

/**
 * 0812测试表 - 数据传输对象
 */
public record Test0812DTO(
        Long id,
        String name,
        String description,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
