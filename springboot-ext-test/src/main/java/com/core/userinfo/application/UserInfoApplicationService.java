package com.core.userinfo.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.userinfo.application.dto.UserInfoDTO;
import com.core.userinfo.domain.model.UserInfoEntity;
import com.core.userinfo.domain.repository.UserInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户信息表 - 应用服务
 */
@Service
@Transactional
public class UserInfoApplicationService {

    private final UserInfoRepository userInfoRepository;

    public UserInfoApplicationService(UserInfoRepository userInfoRepository) {
        this.userInfoRepository = userInfoRepository;
    }

    @Transactional(readOnly = true)
    public UserInfoDTO getById(Long id) {
        UserInfoEntity entity = userInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在，id=" + id));
        return toDTO(entity);
    }

    @Transactional(readOnly = true)
    public Page<UserInfoDTO> page(int pageNum, int pageSize) {
        Page<UserInfoEntity> entityPage = userInfoRepository.page(pageNum, pageSize);
        List<UserInfoDTO> dtoList = entityPage.getRecords().stream()
                .map(this::toDTO)
                .toList();
        Page<UserInfoDTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    public UserInfoDTO create(UserInfoEntity entity) {
        return toDTO(userInfoRepository.save(entity));
    }

    public UserInfoDTO update(Long id, UserInfoEntity entity) {
        entity.setId(id);
        return toDTO(userInfoRepository.save(entity));
    }

    public long delete(Long id) {
        return userInfoRepository.deleteById(id);
    }

    private UserInfoDTO toDTO(UserInfoEntity entity) {
        return new UserInfoDTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
