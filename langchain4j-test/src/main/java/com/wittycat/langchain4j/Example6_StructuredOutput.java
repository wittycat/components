package com.wittycat.langchain4j;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;

import java.util.List;

/**
 * 示例 6:结构化输出 —— 让模型返回强类型 Java 对象,而不是一段散文。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example6_StructuredOutput
 *
 * 【知识点】
 * 1. 业务系统要的是对象不是文本:AiServices 的接口方法返回类型写成 POJO/record,
 *    框架自动完成 "要求模型按 JSON 输出 → 解析 → 反序列化" 全流程,解析失败还会自动重试。
 * 2. record 是最适合承接结构化输出的载体:字段即属性;enum 字段让模型做受限分类
 *    (情感分析是经典用法);List 嵌套也没问题。
 * 3. 方法参数上的 {{it}} 是"未命名参数"占位符(只有一个 String 参数时的简写),
 *    等价于示例 2 里命名变量的 PromptTemplate——@UserMessage 本身就是模板。
 * 4. 没有结构化输出时,大家靠"提示词里求它输出 JSON + 手写正则解析",又脆又丑;
 *    结构化输出把脆弱点收进框架,这也是 1.x 版本的主打能力之一。
 */
public class Example6_StructuredOutput {

    /** 情感枚举:enum 字段 = 让模型做受限分类 */
    enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE
    }

    /**
     * 评论的结构化结果。
     * record 的组件名就是 JSON 字段名,起名要"模型能看懂":title/themes/summary。
     */
    record ReviewAnalysis(String title, Sentiment sentiment, List<String> themes, String summary) {
    }

    /** 声明式接口:返回类型直接是 record / List,框架负责解析 */
    interface ReviewAnalyzer {

        @UserMessage("分析这条商品评论,提取结构化信息:\n{{it}}")
        ReviewAnalysis analyze(String review);

        @UserMessage("从下面的描述里提取所有提到的编程语言名称,只返回语言列表:\n{{it}}")
        List<String> extractLanguages(String description);
    }

    public static void main(String[] args) {
        ReviewAnalyzer analyzer = AiServices.builder(ReviewAnalyzer.class)
                .chatModel(GlmModels.createChatModel(0.1, 1024)) // 低温度:抽取任务要确定性
                .build();

        String review = """
                这个机械键盘太好用了!轴体手感清脆,RGB 灯效炫酷,键帽做工也很好。
                就是有点贵,而且说明书写得不太清楚。总体瑕不掩瑜,强烈推荐!
                """;

        // 1. 拿到的直接是强类型对象,可以直接 if/switch,不用解析文本
        ReviewAnalysis analysis = analyzer.analyze(review);
        System.out.println("标题:   " + analysis.title());
        System.out.println("情感:   " + analysis.sentiment());
        System.out.println("主题:   " + analysis.themes());
        System.out.println("摘要:   " + analysis.summary());

        // 2. List<String>:同样直接是 Java 集合
        String description = "这个项目用 Java 写后端,前端是 TypeScript,脚本用 Python,构建脚本里还有点 Bash。";
        System.out.println("\n提取到的语言: " + analyzer.extractLanguages(description));
    }
}
