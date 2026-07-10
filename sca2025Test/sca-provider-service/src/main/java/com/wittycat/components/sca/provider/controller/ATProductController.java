package com.wittycat.components.sca.provider.controller;

import com.wittycat.components.sca.provider.service.ATProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/atproduct")
public class ATProductController {

    @Autowired
    private ATProductService atProductService;

    /**
     * 根据产品ID更新产品信息
     *
     * @param productId
     * @param productNum
     * @return
     */
    @RequestMapping("/atdeduct")
    public boolean deduct(@RequestParam("productId") int productId, @RequestParam("userId") int userId, @RequestParam("productNum") int productNum) {
       log.info("ProductController 开始执行");
        boolean deduct= atProductService.deduct(productId, userId, productNum);
        return deduct;
    }
}
