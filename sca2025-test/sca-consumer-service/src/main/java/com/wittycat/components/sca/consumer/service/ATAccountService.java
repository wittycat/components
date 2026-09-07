package com.wittycat.components.sca.consumer.service;


public interface ATAccountService {

    boolean deduct(
            Integer userId,
            Integer productId,
            Integer count,
            Double money);

}
