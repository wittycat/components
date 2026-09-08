package com.wittycat.langchain4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

/**
 * 示例 4:工具调用(Function Calling)—— 让 LLM 会"用 API"。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example4_ToolCall
 *
 * 【知识点】
 * 1. LLM 只会生成文本,永远不会真的执行代码。"调用工具"的机制是:
 *      a) 你把"有哪些工具、参数是什么"随请求发给模型(由 @Tool 注解自动生成描述);
 *      b) 模型判断需要时,回复一个"请帮我调用 weather(城市=上海)"的结构化请求;
 *      c) LangChain4j 在本地执行你的 Java 方法,把结果再发回模型;
 *      d) 模型基于工具结果组织最终回答。b-d 可能循环多轮,全部由 AiServices 自动完成。
 * 2. 写一个工具 = 写一个普通 Java 方法 + @Tool 注解:
 *      - @Tool 的 value 是给模型看的功能描述,写得越清楚模型调用越准;
 *      - @P 描述每个参数的含义(模型靠它填参数)。
 *    工具就是普通类,agentscope-test 的 WeatherTools 思路完全一致——"工具"是所有
 *    Agent 框架的通用概念。
 * 3. AiServices:LangChain4j 最强大的 API。你只写一个接口(声明式),
 *    它用 JDK 动态代理生成实现,把 chatModel/tools/记忆/RAG 全部组装进代理里,
 *    对应 Spring 里的"面向接口编程 + 框架生成实现"。
 * 4. 控制台里能看到 [工具被调用] 的日志——那行是你的代码在本地执行的,不是模型输出的。
 */
public class Example4_ToolCall {

    /** 工具就是普通 Java 类:方法 + @Tool 注解,不需要实现任何接口 */
    static class WeatherTools {

        @Tool("查询指定城市的当前天气")
        String weather(@P("城市名,如:北京") String city) {
            System.out.println(">> [工具被调用] weather(" + city + ") —— 这行是本地 Java 代码执行的");
            // 真实项目里这里调天气 API;学习示例返回假数据
            return city + ":晴,26°C,东南风 2 级,湿度 45%";
        }

        @Tool("计算一个字符串表达式的算术结果,支持加减乘除")
        double calculate(@P("算术表达式,如:1+2*3") String expression) {
            System.out.println(">> [工具被调用] calculate(" + expression + ")");
            // 学习示例:只处理 a op b 的最简形式
            String[] ops = {"+", "-", "*", "/"};
            for (String op : ops) {
                int i = expression.indexOf(op);
                if (i > 0) {
                    double a = Double.parseDouble(expression.substring(0, i).trim());
                    double b = Double.parseDouble(expression.substring(i + 1).trim());
                    return switch (op) {
                        case "+" -> a + b;
                        case "-" -> a - b;
                        case "*" -> a * b;
                        default -> a / b;
                    };
                }
            }
            throw new IllegalArgumentException("无法解析表达式: " + expression);
        }
    }

    /** 声明式接口:AiServices 用动态代理生成实现,你永远不用自己写 impl */
    interface WeatherAssistant {

        @SystemMessage("你是一个天气与计算助手,回答前必须先调用工具获取真实数据,不要凭空编造。")
        String chat(String question);
    }

    public static void main(String[] args) {
        WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
                .chatModel(GlmModels.createChatModel())
                .tools(new WeatherTools())
                .build();

        // 这个问题必须先调 weather 工具才能回答,观察控制台里的 [工具被调用] 日志
        System.out.println("问: 上海今天适合穿什么?\n");
        System.out.println("答: " + assistant.chat("上海今天适合穿什么?"));

        System.out.println("\n--------------------\n");
        // 这个问题需要模型自己决定用哪个工具、怎么填参数(两个数字需要它自己算出来)
        System.out.println("问: 我买了 3 斤苹果,每斤 6 块 5,一共多少钱?\n");
        System.out.println("答: " + assistant.chat("我买了 3 斤苹果,每斤 6 块 5,一共多少钱?"));
    }
}
