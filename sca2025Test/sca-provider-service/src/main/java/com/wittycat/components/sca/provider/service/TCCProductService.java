package com.wittycat.components.sca.provider.service;

import org.apache.seata.rm.tcc.api.BusinessActionContext;


public interface TCCProductService {
    /**
     * try逻辑
     */
    boolean deduct(
            BusinessActionContext context,
            Integer productId,
            Integer userId,
            Integer freezeNum);

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
