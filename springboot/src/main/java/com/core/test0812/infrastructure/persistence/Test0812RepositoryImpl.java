package com.core.test0812.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.test0812.domain.model.Test0812Entity;
import com.core.test0812.domain.repository.Test0812Repository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 0812测试表 - 仓库实现（Infrastructure 层）
 */
@Repository
public class Test0812RepositoryImpl implements Test0812Repository {

    private final Test0812Dao test0812Dao;

    public Test0812RepositoryImpl(Test0812Dao test0812Dao) {
        this.test0812Dao = test0812Dao;
    }

    @Override
    public Test0812Entity save(Test0812Entity entity) {
        Test0812PO po = Test0812Mapper.toPO(entity);
        if (po.getId() != null && test0812Dao.selectById(po.getId()) != null) {
            test0812Dao.updateById(po);
        } else {
            test0812Dao.insert(po);
        }
        return Test0812Mapper.toDomain(po);
    }

    @Override
    public Optional<Test0812Entity> findById(Long id) {
        return Optional.ofNullable(test0812Dao.selectById(id))
                .map(Test0812Mapper::toDomain);
    }

    @Override
    public Page<Test0812Entity> page(int pageNum, int pageSize) {
        Page<Test0812PO> poPage = test0812Dao.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Test0812PO>().orderByDesc(Test0812PO::getCreateTime)
        );
        Page<Test0812Entity> entityPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        List<Test0812Entity> records = poPage.getRecords().stream()
                .map(Test0812Mapper::toDomain)
                .toList();
        entityPage.setRecords(records);
        return entityPage;
    }

    @Override
    public long deleteById(Long id) {
        return test0812Dao.deleteById(id);
    }
}
