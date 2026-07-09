package com.wittycat.components.sca.consumer.controller;

import com.wittycat.components.sca.consumer.client.ProviderService;
import com.wittycat.components.sca.consumer.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private ProviderService providerService;

    /**
     * 修改用户账户信息
     *
     * @return
     */
    @RequestMapping("/deduct")
    @GlobalTransactional
    public void deduct(int userId, int productId, int count, double money) {
        boolean deduct = accountService.deduct(null,userId, productId, count, money);
        log.info("deduct="+deduct);
        String deduct1 = providerService.deduct(productId, userId, count);
        log.info("deduct1="+deduct1);
    }

}
