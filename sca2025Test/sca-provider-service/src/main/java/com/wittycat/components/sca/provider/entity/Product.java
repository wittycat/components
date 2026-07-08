package com.wittycat.components.sca.provider.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Product {
    private int id;//产品ID

    private int productNum;//产品剩余数量

    private String productDesc;//产品描述

    private Date createDate;//创建时间

}
