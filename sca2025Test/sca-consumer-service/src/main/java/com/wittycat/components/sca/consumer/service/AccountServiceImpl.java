package com.wittycat.components.sca.consumer.service;

import com.wittycat.components.sca.consumer.entity.Account;
import com.wittycat.components.sca.consumer.entity.AccountFreeze;
import com.wittycat.components.sca.consumer.mapper.AccountFreezeMapper;
import com.wittycat.components.sca.consumer.mapper.AccountMapper;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RefreshScope
@Slf4j
public class AccountServiceImpl implements AccountService {


    @Value("默认地址：${address.name}")
    private String name;


    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFreezeMapper accountFreezeMapper;


    /**
     * @param userId
     * @param money
     */
    @Override
    @Transactional
    public void deduct(Integer userId, Integer productId, Integer count, Double money) {
        //1、获取事务id
        String xid = RootContext.getXID();

        log.info("地址名称:" + name + ";事务ID:" + xid);

        //2、判断freeze中是否有冻结记录。如果有则一定是CANCEL执行过,需要要拒绝业务
        AccountFreeze oldFreeze = accountFreezeMapper.selectById(xid);

        if (oldFreeze != null) {
            log.info("地址名称:" + name + ";CANCEL执行过，需要拒绝业务:");
            //CANCEL执行过，需要拒绝业务
            return;
        }

        //3、用户账户扣减可用余额
        Account account = new Account();
        account.setUserId(userId);
        account.setMoney(money);
        account.setUpdateDate(new Date());
        accountMapper.deduct(account);

        //4、冻结表记录冻结金额、事务状态
        AccountFreeze freeze = new AccountFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeMoney(money);
        freeze.setState(AccountFreeze.State.TRY);
        freeze.setXid(xid);
        accountFreezeMapper.insert(freeze);
        log.info("地址名称:" + name + ";冻结完成");
    }

    /**
     * 提交操作
     *
     * @param context 上下文可以传递try方法里面的参数
     * @return
     */
    @Override
    @Transactional
    public boolean confirm(BusinessActionContext context) {
        // 1、获取事务id
        String xid = context.getXid();

        //2、根据id删除冻结记录
        int count = accountFreezeMapper.deleteById(xid);
        log.info("地址名称:" + name + ";提交完成");
        return count == 1;
    }

    /**
     * 回滚操作
     *
     * @param context
     * @return
     */
    @Override
    @Transactional
    public boolean cancel(BusinessActionContext context) {
        // 1、获取事务ID
        String xid = context.getXid();
        //2、查询freeze表中是否有冻结记录
        AccountFreeze freeze = accountFreezeMapper.selectById(xid);
        String userId = context.getActionContext("userId").toString();
        //3、空回滚的判断:判断freeze是否为null。如果为null则证明try没有执行,需要空回滚
        if (freeze == null) {
            //证明try没执行，需要空回滚
            double money = 0l;
            freeze = new AccountFreeze();
            freeze.setUserId(Integer.valueOf(userId));
            freeze.setFreezeMoney(money);
            freeze.setState(AccountFreeze.State.CANCEL);
            freeze.setXid(xid);
            accountFreezeMapper.insert(freeze);
            return true;
        }

        //4、幂等判断
        if (freeze.getState() == AccountFreeze.State.CANCEL) {
            //已经处理过一次CANCEL,无需重复处理
            return true;
        }

        //5、恢复可用余额
        Account account = new Account();
        account.setUserId(Integer.valueOf(freeze.getUserId()));
        account.setMoney(freeze.getFreezeMoney());
        account.setUpdateDate(new Date());
        accountMapper.refund(account);

        //6、将冻结金额清零，状态改为CANCEL
        double money = 0;
        freeze.setFreezeMoney(money);
        freeze.setState(AccountFreeze.State.CANCEL);
        int count = accountFreezeMapper.updateById(freeze);
        log.info("地址名称:" + name + ";回滚完成");

        return count == 1;
    }
}
