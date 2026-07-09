package com.wittycat.components.sca.consumer.service;


import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.LocalTCC;

@LocalTCC
public interface AccountService {
    /**
     * try逻辑
     *
     * @param userId 用户ID
     * @param money  扣减金额
     */
    boolean deduct(
            BusinessActionContext actionContext,
            Integer userId,
            Integer productId,
            Integer count,
            Double money);
    /**
     * 二阶段confirm确认方法、可以另命名，但要保证与commitMethod一致
     *
     * @param
     * @param context 上下文可以传递try方法里面的参数
     * @return boolean 执行是否成功
     */
    boolean confirm(BusinessActionContext context);

    /**
     * 二阶段回滚方法，要保证与rollbackMethod一致
     *
     * @param context
     * @return
     */
    boolean cancel(BusinessActionContext context);
}
