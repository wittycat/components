package com.core.userinfo.domain.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.userinfo.domain.model.UserInfoEntity;

import java.util.List;
import java.util.Optional;

/**
 * 用户信息表 - 仓库接口（Domain 层只定义接口，依赖倒置）
 */
public interface UserInfoRepository {

    UserInfoEntity save(UserInfoEntity entity);

    Optional<UserInfoEntity> findById(Long id);

    Page<UserInfoEntity> page(int pageNum, int pageSize);

    long deleteById(Long id);
}
