package com.wittycat.components.sca.provider.service.impl;

import com.wittycat.components.sca.provider.entity.Product;
import com.wittycat.components.sca.provider.mapper.ProductMapper;
import com.wittycat.components.sca.provider.service.ATProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.core.context.RootContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ATProductServiceImpl implements ATProductService {
    private static final String productName = "AT商品库存服务";
    @Autowired
    private ProductMapper productMapper;

    /**
     * @param productId
     * @param freezeNum
     */
    @Override
    @Transactional
    public boolean deduct(Integer productId, Integer userId, Integer freezeNum) {
        log.info("产品名称:" + productName);
        //1、获取事务id
        String xid = RootContext.getXID();
        log.info("产品名称:" + productName + ";事务ID:" + xid+" BranchType="+RootContext.getBranchType().name());

        //3、商品库存扣减数量
        Product account = new Product();
        account.setProductNum(freezeNum);
        account.setId(productId);
        productMapper.deduct(account);
        if (1 == 1) {
            throw new RuntimeException("模拟异常");
        }
        log.info("产品名称:" + productName + ";冻结完成");
        return true;
    }
}
