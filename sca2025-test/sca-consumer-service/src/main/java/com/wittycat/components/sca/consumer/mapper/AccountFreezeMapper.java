package com.wittycat.components.sca.consumer.mapper;

import com.wittycat.components.sca.consumer.entity.AccountFreeze;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountFreezeMapper {

    //新增冻结记录
    int insert(AccountFreeze accountFreeze);

    //根据id删除冻结记录
    int deleteById(String xid);

    //修改冻结记录
    int updateById(AccountFreeze accountFreeze);

    //查询freeze中是否有冻结记录
    AccountFreeze selectById(String xid);
}
