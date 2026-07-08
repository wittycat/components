package com.wittycat.components.sca.consumer.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface AccountService {
    /**
     * try逻辑
     *
     * @param userId 用户ID
     * @param money  扣减金额
     * @TwoPhaseBusinessAction注解中的name属性要与当前方法名一致,用于指定Try逻辑对应的方法
     * @TwoPhaseBusinessAction注解中的commitMethod属性要与提交方法名一致,用于指定提交逻辑对应的方法
     * @TwoPhaseBusinessAction注解中的rollbackMethod属性要与回滚方法名一致,用于指定回滚逻辑对应的方法
     */
    @TwoPhaseBusinessAction(name = "deduct", commitMethod = "confirm", rollbackMethod = "cancel")
    void deduct(@BusinessActionContextParameter(paramName = "userId") Integer userId,
                @BusinessActionContextParameter(paramName = "productId") Integer productId,
                @BusinessActionContextParameter(paramName = "count") Integer count,
                @BusinessActionContextParameter(paramName = "money") Double money);

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
