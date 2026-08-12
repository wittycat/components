package com.core.test0812.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.test0812.application.dto.Test0812DTO;
import com.core.test0812.domain.model.Test0812Entity;
import com.core.test0812.domain.repository.Test0812Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 0812测试表 - 应用服务
 */
@Service
@Transactional
public class Test0812ApplicationService {

    private final Test0812Repository test0812Repository;

    public Test0812ApplicationService(Test0812Repository test0812Repository) {
        this.test0812Repository = test0812Repository;
    }

    @Transactional(readOnly = true)
    public Test0812DTO getById(Long id) {
        Test0812Entity entity = test0812Repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在，id=" + id));
        return toDTO(entity);
    }

    @Transactional(readOnly = true)
    public Page<Test0812DTO> page(int pageNum, int pageSize) {
        Page<Test0812Entity> entityPage = test0812Repository.page(pageNum, pageSize);
        List<Test0812DTO> dtoList = entityPage.getRecords().stream()
                .map(this::toDTO)
                .toList();
        Page<Test0812DTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    public Test0812DTO create(Test0812Entity entity) {
        return toDTO(test0812Repository.save(entity));
    }

    public Test0812DTO update(Long id, Test0812Entity entity) {
        entity.setId(id);
        return toDTO(test0812Repository.save(entity));
    }

    public long delete(Long id) {
        return test0812Repository.deleteById(id);
    }

    private Test0812DTO toDTO(Test0812Entity entity) {
        return new Test0812DTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
