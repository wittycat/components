package com.wittycat.springaialibaba.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 6:Spring Boot + DashScope 自动配置 + Graph 流式接口(端口 8085)。
 *
 * 运行方式: mvn -q spring-boot:run
 *
 * 【知识点】
 * 1. 与 CLI 示例的区别只在于"谁建模型":CLI 用 DashScopeModels 手动 build;
 *    这里 spring-ai-alibaba-starter-dashscope 读 spring.ai.dashscope.*(见 application.yml),
 *    容器里自动就有 ChatModel —— 和 spring-ai-test 的 Web 示例同一体验,只是换了 starter。
 * 2. @SpringBootApplication 扫描范围为 web 包,公共类(DashScopeConfig 等)在上级包
 *    com.wittycat.springaialibaba,这里显式声明 scanBasePackages 把它们纳入。
 */
@SpringBootApplication(scanBasePackages = "com.wittycat.springaialibaba")
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
