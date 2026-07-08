package com.wittycat.components.sca.consumer.controller;

import com.wittycat.components.sca.consumer.client.ProductService;
import com.wittycat.components.sca.consumer.service.AccountService;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private ProductService productService;

    /**
     * 修改用户账户信息
     *
     * @return
     */
    @RequestMapping("/deduct")
    @GlobalTransactional
    public void deduct(int userId, int productId, int count, double money) {
        accountService.deduct(userId, productId, count, money);
        productService.deduct(productId, userId, count);
    }

}
