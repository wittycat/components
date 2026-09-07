package com.wittycat.components.sca.consumer.mapper;

import com.wittycat.components.sca.consumer.entity.Account;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper {

    //扣减金额
    int deduct(Account account);

    //恢复金额
    int refund(Account account);
}
