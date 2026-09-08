package com.wittycat.springaialibaba;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 示例 5:ReactAgent —— Spring AI Alibaba 的 Agent 抽象(1.1.x 主打能力)。
 * 一行 builder 把 模型 + 工具 + 系统提示词 + 结构化输出 + 记忆 组装成"一个 Agent 对象"。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springaialibaba.Example5_ReactAgent
 *
 * 【知识点】
 * 1. ChatClient.tools() 也能做工具调用,但那只是"带工具的一次请求";ReactAgent 在其上包了一层
 *    Agent 生命周期:内部就是一张 Graph(ReAct 循环:思考→调工具→观察→再思考),
 *    因此天然支持 MemorySaver 会话持久化、输出类型约束、Hook 扩展、流式事件等"Agent 级"能力。
 * 2. 本例组装的 IT 支持 Agent:
 *      .methodTools(...)   挂 @Tool 工具对象(和 spring-ai-test 示例 4 完全同一套注解,直接复用);
 *      .outputType(...)    约束最终输出为指定类型(框架生成 JSON Schema 交给模型,返回 JSON 文本);
 *      .saver(new MemorySaver())  按 threadId 保存会话状态,多轮对话有记忆(生产可换数据库 CheckPointer)。
 * 3. 多轮记忆:两次 call 传同一个 threadId,第二次模型"记得"第一次查过的工单 —— 没有把历史手动拼进 prompt。
 * 4. Agent 返回的 AssistantMessage 文本是 JSON(outputType 约束),用 Jackson 反序列化成 record 即拿到类型安全结果。
 */
public class Example5_ReactAgent {

    /** Agent 的结构化输出类型:输出约束在 builder 上,调用方不用每次提醒格式 */
    record TroubleReport(String title, String severity, String advice) {
    }

    public static void main(String[] args) throws Exception {
        ReactAgent agent = ReactAgent.builder()
                .name("it-support-agent")
                .model(DashScopeModels.createChatModel())
                .methodTools(new TicketTools())
                .systemPrompt("""
                        你是 IT 支持工程师。处理用户问题时必须先用工具核实信息:
                        涉及工单的先调 getTicketStatus,涉及处理方法的先调 searchDocs,再给结论。
                        最终输出:title(问题一句话概括)、severity(P0~P3)、advice(处理建议)。""")
                .outputType(TroubleReport.class)
                .saver(new MemorySaver())
                .build();

        // 同一个 threadId = 同一个会话,MemorySaver 保存其状态
        RunnableConfig session = RunnableConfig.builder().threadId("user-001").build();

        String q1 = "我的工单 T-1024 现在处理到哪一步了?严重吗?";
        System.out.println("第 1 轮: " + q1 + "\n");
        AssistantMessage first = agent.call(q1, session);
        printReport(first);

        String q2 = "针对这个工单的问题,给我一句话处理建议";
        System.out.println("\n第 2 轮(考验记忆,没重复工单号): " + q2 + "\n");
        AssistantMessage second = agent.call(q2, session);
        printReport(second);
    }

    /** outputType 约束下 Agent 返回的是 JSON 文本,反序列化成 record */
    static void printReport(AssistantMessage message) throws Exception {
        System.out.println("Agent 原始输出(JSON): " + message.getText());
        TroubleReport report = new ObjectMapper().readValue(message.getText(), TroubleReport.class);
        System.out.printf("解析结果: [%s] %s%n  建议: %s%n", report.severity(), report.title(), report.advice());
    }

    /** 工具集合:@Tool 注解与 Spring AI 完全同一套(spring-ai-test 示例 4 的写法直接复用) */
    static class TicketTools {

        /** 模拟的工单表(真实项目里这里是调 ITSM 系统 / 查数据库) */
        private static final java.util.Map<String, String> TICKETS = java.util.Map.of(
                "T-1024", "{\"status\":\"处理中\",\"severity\":\"P1\",\"owner\":\"王工\",\"summary\":\"生产环境登录接口 5xx\"}");

        @Tool(description = "按工单号查询工单的状态、等级、负责人和摘要")
        String getTicketStatus(@ToolParam(description = "工单号,例如 T-1024") String ticketId) {
            System.out.println(">> [工具被调用] getTicketStatus(" + ticketId + ") —— 这行是你的 Java 代码执行的");
            return TICKETS.getOrDefault(ticketId, "{\"error\":\"工单不存在\"}");
        }

        @Tool(description = "按关键词搜索内部运维知识库,返回处理方案")
        String searchDocs(@ToolParam(description = "关键词,例如 登录5xx") String keyword) {
            System.out.println(">> [工具被调用] searchDocs(" + keyword + ") —— 这行是你的 Java 代码执行的");
            return "知识库《生产登录 5xx 应急手册》:1)查网关日志定位实例;2)重启异常实例;3)观察 10 分钟后扩容。";
        }
    }
}
