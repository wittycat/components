package com.core.userinfo.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.userinfo.domain.model.UserInfoEntity;
import com.core.userinfo.domain.repository.UserInfoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户信息表 - 仓库实现（Infrastructure 层）
 */
@Repository
public class UserInfoRepositoryImpl implements UserInfoRepository {

    private final UserInfoDao userInfoDao;

    public UserInfoRepositoryImpl(UserInfoDao userInfoDao) {
        this.userInfoDao = userInfoDao;
    }

    @Override
    public UserInfoEntity save(UserInfoEntity entity) {
        UserInfoPO po = UserInfoMapper.toPO(entity);
        if (po.getId() != null && userInfoDao.selectById(po.getId()) != null) {
            userInfoDao.updateById(po);
        } else {
            userInfoDao.insert(po);
        }
        return UserInfoMapper.toDomain(po);
    }

    @Override
    public Optional<UserInfoEntity> findById(Long id) {
        return Optional.ofNullable(userInfoDao.selectById(id))
                .map(UserInfoMapper::toDomain);
    }

    @Override
    public Page<UserInfoEntity> page(int pageNum, int pageSize) {
        Page<UserInfoPO> poPage = userInfoDao.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserInfoPO>().orderByDesc(UserInfoPO::getCreateTime)
        );
        Page<UserInfoEntity> entityPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        List<UserInfoEntity> records = poPage.getRecords().stream()
                .map(UserInfoMapper::toDomain)
                .toList();
        entityPage.setRecords(records);
        return entityPage;
    }

    @Override
    public long deleteById(Long id) {
        return userInfoDao.deleteById(id);
    }
}
