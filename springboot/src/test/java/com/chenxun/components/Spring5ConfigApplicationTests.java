package com.chenxun.components;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;


/**
 * Created by chenxun.
 * Date: 2018/5/14 上午12:44
 * Description:
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes=Spring5ConfigApplication.class)
//@ContextConfiguration(classes=Spring5ConfigApplication.class)
public class Spring5ConfigApplicationTests {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    public void contextLoads() {

        Assert.assertNotNull("the BarService should not be null.",applicationContext.getBean(BarService.class));
        Assert.assertNotNull("the FooService should not be null.",applicationContext.getBean(FooService.class));

    }

}