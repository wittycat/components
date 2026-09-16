package com.wittycat.pgvector;

import com.wittycat.SpringAiApplication;
import com.wittycat.pgvector.rag.KnowledgeBase;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 示例 11:灌库 —— 语料切块、embedding 后写入 PG 的 vector_store 表。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.pgvector.Example11_Ingest
 * 验证: psql -d rag_demo -c "SELECT left(content, 30), metadata FROM vector_store LIMIT 3;"
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
public class Example11_Ingest {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SpringAiApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            int chunks = ctx.getBean(KnowledgeBase.class).ingest();
            System.out.println("已写入 " + chunks + " 个 chunk 到 vector_store 表");

            Integer rows = ctx.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT count(*) FROM vector_store", Integer.class);
            System.out.println("表内总行数 = " + rows + "(embedding 列维度见 application.yml 的 dimensions)");
        }
    }
}
