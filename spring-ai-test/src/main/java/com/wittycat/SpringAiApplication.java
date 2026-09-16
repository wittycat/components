package com.wittycat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 9:全模块唯一的 Spring Boot 启动类,把 CLI 示例学的两大能力挂成 Web 接口。
 *
 * 运行方式:
 *   mvn -q spring-boot:run            (或 IDE 运行本类的 main;前置:Postgres.app 已启动)
 * 启动后同在 8084 端口:
 *   GET  /chat/stream?message=...&sessionId=...   SSE 流式聊天(实现在 web/ChatController)
 *   POST /rag/ingest | GET /rag/search | GET /rag/chat   RAG 三接口(实现在 pgvector/web/RagController)
 *
 * 【知识点】
 * 1. Spring AI 是"长在 Spring 里"的框架,和 langchain4j-test 示例 11 的最大区别在装配方式:
 *    starter 自动配置读了 application.yml 里的 spring.ai.openai.*(它用 ${glm.xxx} 占位符
 *    和统一的 glm.* 约定打通),容器里就有了 ChatModel / EmbeddingModel / ChatClient.Builder;
 *    再加上 pgvector starter 的 DataSource + PgVectorStore,业务代码只管注入,零装配代码。
 *    CLI 示例 10~12 也复用本类启动上下文(以 web(NONE) 临时拉起,不占端口)。
 * 2. 组件扫描的边界:启动类默认只扫本包及子包。本模块有 com.wittycat.springai 和
 *    com.wittycat.pgvector 两个平级的业务包,所以启动类放在它们的公共包根 com.wittycat,
 *    web/ 和 pgvector/ 里的 @RestController / @Component 才都会被默认扫描装配——
 *    启动类若放在某个子包(比如 springai/),平级的 pgvector/ 会整个漏扫,
 *    /rag/* 直接 404、CLI 示例 11 拿不到 KnowledgeBase Bean,这是分包后最常见的启动坑。
 * 3. 端口 8084:仓库内的端口约定是 ai-agent 8080、agentscope-test 8081、
 *    langgraph4j-test 预留 8082、langchain4j-test 8083,本模块顺延(见 application.yml)。
 * 4. 自动配置是条件装配:引了 pgvector starter,DataSource/PGVectorStore 就生效,
 *    本应用启动即连 PG(自动建 vector_store 表);若某个应用不想要某项自动配置,
 *    可在 @SpringBootApplication 的 exclude 里精确关掉——比如只想跑 SSE 不连库时,
 *    排除 DataSourceAutoConfiguration + PgVectorStoreAutoConfiguration 两项即可。
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
@SpringBootApplication
public class SpringAiApplication {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringAiApplication.class, args);
    }
}
