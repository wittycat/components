package com.wittycat.components.sca.consumer.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Account {

    private int id;

    private int userId;  //用户ID

    private double money;//剩余金额

    private Date updateDate;//更新时间

    private Date createDate;//创建时间
}
