package com.core.userinfo.infrastructure.persistence;

import com.core.userinfo.domain.model.UserInfoEntity;

/**
 * 用户信息表 - PO ↔ Entity 静态转换器
 */
public final class UserInfoMapper {

    private UserInfoMapper() {
    }

    public static UserInfoEntity toDomain(UserInfoPO po) {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setId(po.getId());
        entity.setName(po.getName());
        entity.setDescription(po.getDescription());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    public static UserInfoPO toPO(UserInfoEntity entity) {
        UserInfoPO po = new UserInfoPO();
        po.setId(entity.getId());
        po.setName(entity.getName());
        po.setDescription(entity.getDescription());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }
}
