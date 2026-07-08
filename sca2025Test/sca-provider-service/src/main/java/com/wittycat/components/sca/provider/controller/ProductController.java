package com.wittycat.components.sca.provider.controller;

import com.wittycat.components.sca.provider.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 根据产品ID更新产品信息
     *
     * @param productId
     * @param productNum
     * @return
     */
    @RequestMapping("/deduct")
    public void deduct(@RequestParam("productId") int productId, @RequestParam("userId") int userId, @RequestParam("productNum") int productNum) {
        System.out.println("ProductController 开始执行");
        productService.deduct(productId, userId, productNum);
    }
}
