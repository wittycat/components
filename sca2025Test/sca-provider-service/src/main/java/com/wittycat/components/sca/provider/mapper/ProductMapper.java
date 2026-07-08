package com.wittycat.components.sca.provider.mapper;


import com.wittycat.components.sca.provider.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper {
    //扣减金额
    int deduct(Product account);

    //恢复金额
    int refund(Product account);
}
