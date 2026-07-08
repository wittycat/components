package com.wittycat.components.sca.provider.service;

import com.wittycat.components.sca.provider.entity.Product;
import com.wittycat.components.sca.provider.entity.ProductFreeze;
import com.wittycat.components.sca.provider.mapper.ProductFreezeMapper;
import com.wittycat.components.sca.provider.mapper.ProductMapper;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RefreshScope
public class ProductServiceImpl implements ProductService {

    @Value("${product.name}")
    private String productName;

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
    public void deduct(Integer productId, Integer userId, Integer freezeNum) {
        System.out.println("产品名称:" + productName);
        //1、获取事务id
        String xid = RootContext.getXID();
        System.out.println("产品名称:" + productName + ";事务ID:" + xid);
        //2、判断freeze中是否有冻结记录,如果有则一定是CANCEL执行过,需要要拒绝业务
        ProductFreeze oldFreeze = productFreezeMapper.selectById(xid);

        if (oldFreeze != null) {
            System.out.println("产品名称:" + productName + ";CANCEL执行过，需要拒绝业务" + xid);
            //CANCEL执行过，需要拒绝业务
            return;
        }

        //3、商品库存扣减数量
        Product account = new Product();
        account.setProductNum(freezeNum);
        account.setId(productId);
        productMapper.deduct(account);
        if(1==1){
            throw new RuntimeException("模拟异常");
        }
        //4、冻结表记录冻结数量、事务状态
        ProductFreeze freeze = new ProductFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeNum(freezeNum);
        freeze.setState(ProductFreeze.State.TRY);
        freeze.setXid(xid);
        productFreezeMapper.insert(freeze);
        System.out.println("产品名称:" + productName + ";冻结完成");

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
        int count = productFreezeMapper.deleteById(xid);
        System.out.println("产品名称:" + productName + ";提交完成");
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
        String userId = context.getActionContext("userId").toString();
        //3、空回滚的判断:判断freeze是否为null。如果为null则证明try没有执行,需要空回滚
        if (freeze == null) {
            //证明try没执行，需要空回滚
            freeze = new ProductFreeze();
            freeze.setUserId(Integer.valueOf(userId));
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
        System.out.println("产品名称:" + productName + ";回滚完成");
        return count == 1;
    }
}
