package com.core.test0812.infrastructure.persistence;

import com.core.test0812.domain.model.Test0812Entity;

/**
 * 0812测试表 - PO ↔ Entity 静态转换器
 */
public final class Test0812Mapper {

    private Test0812Mapper() {
    }

    public static Test0812Entity toDomain(Test0812PO po) {
        Test0812Entity entity = new Test0812Entity();
        entity.setId(po.getId());
        entity.setName(po.getName());
        entity.setDescription(po.getDescription());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    public static Test0812PO toPO(Test0812Entity entity) {
        Test0812PO po = new Test0812PO();
        po.setId(entity.getId());
        po.setName(entity.getName());
        po.setDescription(entity.getDescription());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }
}
