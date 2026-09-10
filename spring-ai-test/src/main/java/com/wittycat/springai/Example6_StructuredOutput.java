package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

/**
 * 示例 6:结构化输出 —— 让模型按 Java 类型返回数据,业务代码直接拿强类型对象。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example6_StructuredOutput
 *
 * 【知识点】
 * 1. 大模型天然输出自由文本,但业务代码需要的是对象。call().entity(类型) 做了三件事:
 *      ① 由 BeanOutputConverter 按目标类型生成 JSON Schema,附到请求里约束模型;
 *      ② 模型按 Schema 输出 JSON;
 *      ③ 自动反序列化成 Java 对象(record / POJO / enum / List 都支持)。
 *    不用手写解析,也不用自己拼"请输出 JSON"的提示词。
 * 2. record + enum + List 组合是最常用的抽取模型:情感是 enum(限定取值),
 *    优缺点是 List,摘要是一句话——类型即约束,字段即文档。
 * 3. 泛型类型(如 List<String>)需要用 ParameterizedTypeReference 保留运行时泛型信息,
 *    这是 Java 类型擦除下的标准做法(Spring 的 RestTemplate / WebClient 同款)。
 * 4. 对照 langchain4j-test 示例 6:那边把返回类型直接声明在 AiServices 接口方法上,
 *    这边在调用链末尾 .entity(...) 显式声明——同样的"类型驱动",挂载点不同。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example6_StructuredOutput {

    /**
     * 情感倾向:enum 限定取值,模型只能三选一。
     * 【原理】BeanOutputConverter 生成 JSON Schema 时,会把枚举常量名列为该字段的可选值,
     * 模型按常量名输出(POSITIVE / NEUTRAL / NEGATIVE),Jackson 再按名字反序列化成枚举;
     * 模型一旦输出了枚举之外的内容,解析就会失败——这正是"类型即约束"的含义。
     */
    enum Sentiment {
        /** 好评 */
        POSITIVE,
        /** 中评 */
        NEUTRAL,
        /** 差评 */
        NEGATIVE
    }

    /** 评价分析结果:record 字段即 JSON 字段,嵌套类型自动推导 */
    record ReviewAnalysis(Sentiment sentiment, List<String> pros, List<String> cons, String summary) {
    }

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        ChatClient chatClient = ChatClient.create(GlmModels.createChatModel());

        String review = """
                这个键盘买了三个月了,手感非常好,轴体敲击声音很清脆,RGB 灯效也好看。
                但是续航太差了,不开灯也只能用一周,而且蓝牙偶尔断连,价格还贵。
                总体来说有好有坏吧。
                """;

        // 1. record + enum:一次调用拿到强类型分析结果
        ReviewAnalysis analysis = chatClient.prompt()
                .user(u -> u.text("分析这条商品评价,提取情感倾向、优点列表、缺点列表和一句话摘要:\n{review}")
                        .param("review", review))
                .call()
                .entity(ReviewAnalysis.class);

        System.out.println("情感倾向: " + analysis.sentiment());
        System.out.println("优点:     " + analysis.pros());
        System.out.println("缺点:     " + analysis.cons());
        System.out.println("摘要:     " + analysis.summary());

        // 2. 泛型类型:ParameterizedTypeReference 保留 List<String> 的泛型信息
        List<String> keywords = chatClient.prompt()
                .user("给出 5 个学习 Spring AI 的搜索关键词,只返回关键词本身。")
                .call()
                .entity(new ParameterizedTypeReference<List<String>>() {
                });
        System.out.println("\n关键词: " + keywords);
    }
}
