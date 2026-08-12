package com.core.test0812.domain.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.test0812.domain.model.Test0812Entity;

import java.util.List;
import java.util.Optional;

/**
 * 0812测试表 - 仓库接口（Domain 层只定义接口，依赖倒置）
 */
public interface Test0812Repository {

    Test0812Entity save(Test0812Entity entity);

    Optional<Test0812Entity> findById(Long id);

    Page<Test0812Entity> page(int pageNum, int pageSize);

    long deleteById(Long id);
}
