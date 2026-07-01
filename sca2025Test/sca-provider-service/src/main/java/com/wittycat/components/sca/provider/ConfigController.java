package com.wittycat.components.sca.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by chenxun.
 * Date: 2026/6/30 22:51
 * Description:
 */
@RestController
@RefreshScope   // 关键注解：标记该类中的配置属性支持动态刷新
@Slf4j
public class ConfigController {

    @Value("${demo.config.message:未配置任何消息}")
    private String message;

    @Value("${server.port}")
    private int port;

    @GetMapping("/config")
    public String getConfig() {
        log.info(" message={}, port={} ",message,port);
        return "当前服务端口：" + port + "，从 Nacos 读取的配置值：" + message;
    }
}