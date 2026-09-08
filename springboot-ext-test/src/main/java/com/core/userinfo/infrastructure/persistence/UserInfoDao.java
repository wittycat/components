package com.core.userinfo.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户信息表 - MyBatis-Plus Mapper
 */
@Mapper
public interface UserInfoDao extends BaseMapper<UserInfoPO> {
}
