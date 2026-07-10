package com.wittycat.components.sca.consumer.service.imp;

import com.wittycat.components.sca.consumer.entity.Account;
import com.wittycat.components.sca.consumer.mapper.AccountMapper;
import com.wittycat.components.sca.consumer.service.ATAccountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.core.context.RootContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class ATAccountServiceImpl implements ATAccountService {
    private final static String name = "at-账户服务";
    @Autowired
    private AccountMapper accountMapper;

    /**
     * @param userId
     * @param money
     */
    @Override
    @Transactional
    public boolean deduct(Integer userId, Integer productId, Integer count, Double money) {
        //1、获取事务id
        String xid = RootContext.getXID();
        log.info("产品名称:" + name + ";事务ID:" + xid+" BranchType="+RootContext.getBranchType().name());
        //3、用户账户扣减可用余额
        Account account = new Account();
        account.setUserId(userId);
        account.setMoney(money);
        account.setUpdateDate(new Date());
        int deduct = accountMapper.deduct(account);
        log.info("地址名称:" + name + ";冻结完成");
        return deduct > 0;
    }
}
