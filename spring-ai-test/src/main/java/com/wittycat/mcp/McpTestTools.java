package com.wittycat.mcp;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * MCP 服务篇:几个测试工具 —— 把普通 Java 方法暴露成 MCP tools,
 * Apifox/Cursor/Claude 等 MCP 客户端连上 /mcp 后即可发现并调用它们。
 *
 * 【知识点】MCP(Model Context Protocol)的三个角色,类比 REST 世界:
 * 1. MCP Host:装着 AI 的应用(Apifox/Claude/Cursor),由它的模型决定何时调工具;
 * 2. MCP Client:Host 里的连接器,负责协议握手、发现工具、转发调用(客户端不用自己写);
 * 3. MCP Server:本应用 —— 把 tools/resources/prompts 暴露在 Streamable HTTP 端点上。
 * 本类只写普通方法 + @Tool 注解;注册进 MCP Server 见 McpServerConfig,端点配置见 application.yml。
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
@Service
public class McpTestTools {

    private static final Logger log = LoggerFactory.getLogger(McpTestTools.class);

    private final VectorStore vectorStore;

    public McpTestTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 最小连通性验证:客户端把参数原样拿回去 */
    @Tool(description = "回显消息,验证 MCP 连通的最小工具")
    public String echo(@ToolParam(description = "要原样返回的消息") String message) {
        return "echo: " + message;
    }

    @Tool(description = "获取当前时间,可选时区参数(如 Asia/Shanghai、UTC),默认系统时区")
    public String getCurrentTime(
            @ToolParam(description = "IANA 时区名,可省略", required = false) String timezone) {
        ZoneId zone = (timezone == null || timezone.isBlank())
                ? ZoneId.systemDefault()
                : ZoneId.of(timezone);
        return ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "两数相加,演示数值参数的表单校验")
    public double add(
            @ToolParam(description = "加数 a") double a,
            @ToolParam(description = "加数 b") double b) {
        return a + b;
    }

    /**
     * "业务能力上 MCP"的样子:任何能注入的 Bean(这里是 PgVector 知识库)都能包装成工具,
     * 连上来的客户端不写一行 Java 就能用上本模块的 RAG 检索。
     */
    @Tool(description = "在知识库里做语义检索,返回最相关的 3 段内容(需要 Postgres 已启动且已跑示例 11 灌库)")
    public String ragSearch(@ToolParam(description = "要检索的问题") String query) {
        try {
            List<Document> hits = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(3).build());
            if (hits == null || hits.isEmpty()) {
                return "没有检索到相关内容(知识库可能为空,先运行示例 11 灌库)";
            }
            StringBuilder sb = new StringBuilder("top").append(hits.size()).append(" 相关段落:\n");
            for (Document d : hits) {
                String brief = d.getText().replace("\n", " ");
                if (brief.length() > 80) {
                    brief = brief.substring(0, 80) + "…";
                }
                sb.append(String.format("score=%.4f  %s%n", d.getScore(), brief));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("MCP tool ragSearch 检索失败, query={}", query, e);
            return "检索失败:Postgres 可能未启动,请先启动 Postgres.app 再试";
        }
    }
}
