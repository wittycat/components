package com.wittycat.components.sca.provider.service.impl;

import com.wittycat.components.sca.provider.entity.Product;
import com.wittycat.components.sca.provider.entity.ProductFreeze;
import com.wittycat.components.sca.provider.mapper.ProductFreezeMapper;
import com.wittycat.components.sca.provider.mapper.ProductMapper;
import com.wittycat.components.sca.provider.service.TCCProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@LocalTCC
@RefreshScope
public class TCCProductServiceImpl implements TCCProductService {

    @Value("${product.name:商品库存服务}")
    private String productName = "";

    @Autowired
    ProductMapper productMapper;

    @Autowired
    ProductFreezeMapper productFreezeMapper;

    /**
     * @param productId
     * @param freezeNum
     */
    @Override
    @Transactional
    @TwoPhaseBusinessAction(name = "deduct", commitMethod = "confirm", rollbackMethod = "cancel")
    public boolean deduct(
            BusinessActionContext context,
            @BusinessActionContextParameter(paramName = "productId") Integer productId,
            @BusinessActionContextParameter(paramName = "userId") Integer userId,
            @BusinessActionContextParameter(paramName = "freezeNum") Integer freezeNum) {
        log.info("产品名称:" + productName);
        //1、获取事务id
        String xid = RootContext.getXID();
        log.info("产品名称:" + productName + ";事务ID:" + xid+" BranchType="+RootContext.getBranchType().name());
        //2、判断freeze中是否有冻结记录,如果有则一定是CANCEL执行过,需要要拒绝业务
        ProductFreeze oldFreeze = productFreezeMapper.selectById(xid);

        if (oldFreeze != null) {
            log.info("产品名称:" + productName + ";CANCEL执行过，需要拒绝业务" + xid);
            //CANCEL执行过，需要拒绝业务
            return false;
        }

        //3、商品库存扣减数量
        Product account = new Product();
        account.setProductNum(freezeNum);
        account.setId(productId);
        productMapper.deduct(account);
//        if (1 == 1) {
//            throw new RuntimeException("模拟异常");
//        }
        //4、冻结表记录冻结数量、事务状态
        ProductFreeze freeze = new ProductFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeNum(freezeNum);
        freeze.setState(ProductFreeze.State.TRY);
        freeze.setXid(xid);
        productFreezeMapper.insert(freeze);
        log.info("产品名称:" + productName + ";冻结完成");
        return true;
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
        //6、将冻结金额清零，状态改为CANCEL
        ProductFreeze freeze = productFreezeMapper.selectById(xid);
        freeze.setFreezeNum(0);
        freeze.setXid(xid);
        freeze.setState(ProductFreeze.State.CONFIRM);
        int count = productFreezeMapper.updateById(freeze);
        log.info("产品名称:" + productName + ";提交完成");
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
        ProductFreeze freeze = productFreezeMapper.selectById(xid);
        Integer userId = context.getActionContext("userId",Integer.class);
        //3、空回滚的判断:判断freeze是否为null。如果为null则证明try没有执行,需要空回滚
        if (freeze == null) {
            //证明try没执行，需要空回滚
            freeze = new ProductFreeze();
            freeze.setUserId(userId);
            freeze.setFreezeNum(0);
            freeze.setState(ProductFreeze.State.CANCEL);
            freeze.setXid(xid);
            productFreezeMapper.insert(freeze);
            return true;
        }

        //4、幂等判断
        if (freeze.getState() == ProductFreeze.State.CANCEL) {
            //已经处理过一次CANCEL,无需重复处理
            return true;
        }

        //5、恢复剩余数量
        Product account = new Product();
        account.setProductNum(freeze.getFreezeNum());
        account.setId(Integer.valueOf(context.getActionContext("productId").toString()));
        productMapper.refund(account);

        //6、将冻结金额清零，状态改为CANCEL
        freeze.setFreezeNum(0);
        freeze.setState(ProductFreeze.State.CANCEL);
        int count = productFreezeMapper.updateById(freeze);
        log.info("产品名称:" + productName + ";回滚完成");
        return count == 1;
    }
}
