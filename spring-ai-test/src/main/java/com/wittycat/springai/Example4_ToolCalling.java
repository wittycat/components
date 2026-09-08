package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * 示例 4:工具调用 —— 让模型"调用"你的 Java 方法获取实时信息。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example4_ToolCalling
 *
 * 【知识点】
 * 1. 大模型的训练数据有截止日期,也拿不到你系统里的私有数据;工具调用(Function Calling)
 *    是让它"联网/查库"的标准姿势:@Tool 标注普通 Java 方法,框架把方法签名转成
 *    JSON Schema 的函数描述随请求发给模型。
 * 2. 分工:模型只决定"调哪个工具、参数是什么"(它输出结构化调用指令),
 *    真正执行工具的是你的本地方法;执行结果回传给模型,继续组织自然语言回答。
 *    这整个循环由框架自动完成,业务代码只有 .tools(new XxxTools()) 一行。
 * 3. @ToolParam 给参数补充描述,帮助模型更准确地填参;参数支持 String/数字/boolean 等基础类型。
 * 4. 概念与 langchain4j-test 示例 4 完全互通(那边也是 @Tool,包名不同),对照着看更快。
 */
public class Example4_ToolCalling {

    public static void main(String[] args) {
        ChatClient chatClient = ChatClient.create(GlmModels.createChatModel());

        String question = "北京和上海现在的天气怎么样?两地气温相差多少度?";
        System.out.println("问题: " + question + "\n");

        // .tools() 注册工具对象,框架自动完成"发现工具 -> 模型决策 -> 本地执行 -> 结果回传"循环
        String answer = chatClient.prompt()
                .user(question)
                .tools(new WeatherTools())
                .call()
                .content();

        System.out.println("\n模型回答:\n" + answer);
    }

    /** 工具集合:任何 Spring Bean 或普通对象,方法标 @Tool 即可被模型调用 */
    static class WeatherTools {

        /** 模拟的城市天气表(真实项目里这里是调天气 API / 查数据库) */
        private static final Map<String, String> WEATHER = Map.of(
                "北京", "晴,12 度,空气质量优",
                "上海", "小雨,18 度,湿度较大");

        @Tool(description = "获取指定城市的当前天气,包括天气现象和气温")
        String getWeather(@ToolParam(description = "城市名称,例如:北京") String city) {
            // 这行打印能直观看到"是本地代码在执行工具",模型只是发起了调用
            System.out.println(">> [工具被调用] getWeather(" + city + ") —— 这行是你的 Java 代码执行的");
            return WEATHER.getOrDefault(city, "暂无该城市的天气数据");
        }

        @Tool(description = "计算两个数的差值,返回绝对值")
        double temperatureDiff(@ToolParam(description = "第一个温度") double a,
                               @ToolParam(description = "第二个温度") double b) {
            System.out.printf(">> [工具被调用] temperatureDiff(%.1f, %.1f) —— 这行是你的 Java 代码执行的%n", a, b);
            return Math.abs(a - b);
        }
    }
}
