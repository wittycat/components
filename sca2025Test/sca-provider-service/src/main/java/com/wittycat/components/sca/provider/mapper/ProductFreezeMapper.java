package com.wittycat.components.sca.provider.mapper;


import com.wittycat.components.sca.provider.entity.ProductFreeze;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductFreezeMapper {

    //新增冻结记录
    int insert(ProductFreeze accountFreeze);

    //根据id删除冻结记录
    int deleteById(String xid);

    //修改冻结记录
    int updateById(ProductFreeze accountFreeze);

    //查询freeze中是否有冻结记录
    ProductFreeze selectById(String xid);
}
