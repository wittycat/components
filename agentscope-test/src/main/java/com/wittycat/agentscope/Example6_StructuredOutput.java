package com.wittycat.agentscope;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 示例 6:结构化输出(Structured Output)——让模型"填表"而不是"写作文",稳定拿到 Java 对象。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example6_StructuredOutput
 *
 * 【知识点】(内部机制已对 agentscope-core 2.0.2 反编译核实)
 * 1. 为什么需要它:提示词里要 JSON,模型可能裹一层 Markdown 代码块、字段名随手改、数字给成字符串
 *    ——自由文本的格式永远不可控。解决思路是"数据接收工具":让模型把结果当"工具参数"来填。
 * 2. Part 2 是框架内置写法(推荐):和普通 call 的唯一差别是多传一个目标 Class。
 *    内部实现:框架把 Class 生成 JSON Schema,注册成一个名为 generate_response 的数据接收工具
 *    (ReActAgent.STRUCTURED_OUTPUT_TOOL_NAME),工具描述就是约束:"Generate the final structured
 *    response. Call this function when you have all the information needed..."。
 *    模型一旦调用该工具,ReAct 循环立即截停,工具参数解包后挂到结果 Msg 上,循环结束。
 *    若底层模型声明支持原生结构化输出,则改走 response_format(json_schema)通道,失败自动回退到工具方式。
 * 3. Part 3 是手动写法(一些教程/老版本的风格):自己定义"数据接收工具"(@Tool),
 *    并在 System Prompt 里强制模型必须调用这个工具来提交结果。任何支持 function calling 的模型都能跑,
 *    但强制只是提示词级别的——模型可能不调工具直接回文本,getCaptured() 里的兜底报错演示了这个风险。
 * 4. 取结果两种姿势:
 *      Part 2:hasStructuredData() 判断 + getStructuredData(MovieReview.class) 直接拿对象;
 *      Part 3:工具方法入参就是模型填的"表",AgentScope 已按 @ToolParam 反序列化,方法体里捕获即可。
 * 5. 目标 Class 就是普通 POJO:public 字段 + 无参构造函数,支持 List/Map 和嵌套对象,
 *    可用 Jackson 注解(如 @JsonProperty)微调字段名。
 * 6. 【严谨性提醒】两种写法都只是保证"结果必须以工具参数(JSON)的形态交付",
 *    字段值的正确性(评分范围/枚举合法)仍是模型生成的,入库前要业务校验兜底——本例演示评分越界检查。
 * 7. 【长文本实测结论】(1043 字符影评,GLM 连跑三轮):
 *    - 不加 @JsonProperty(required=true) 时,框架内置路径(Part 2)系统性丢字段(片名/亮点为 null),
 *      因为 Schema 默认不标 required,长文本下模型注意力被稀释,可填可不填的字段它就跳过了;
 *      手动工具(Part 3)每个参数都是 required=true,三轮全字段完整。
 *    - 加上 required=true 后丢字段修复,但"描述里的软约束"依然不设防:
 *      情感倾向仍返回自由文本("正面(带保留)")而不是约定的 POSITIVE/NEGATIVE/NEUTRAL;
 *      亮点无视"2~5个"的说明直接倾倒 30+ 条;甚至把"强烈推荐"的影评填成 recommend=false。
 *      ——Schema 只能约束"有没有、是什么类型",约束不了"值必须属于某枚举/必须符合事实",
 *        这些要么用真正的 enum 类型进 Schema,要么响应侧校验加重试。
 * 8. 【多线程实测结论】(3 线程并发同一影评,两轮):
 *    - 共享 Agent 并发(不同 sessionId 也不行):严重劣化——一轮某线程幻觉出影评里根本不存在的
 *      电影"The Silent Circuit"(评分3.5);另一轮亮点集体缺失/中英文混杂/推荐值互相矛盾。
 *      2.0.2 没有并发护栏:Builder.checkRunning 在整个 core 里没有任何实现。
 *    - 独立 Agent + 共享 Model:同样劣化(亮点14条超上限、评分null、片名给英文)——Model 实例也别共享。
 *    - 每线程独立 Agent + 独立 Model(Part 3 写法):两轮 6/6 全对且结果完全一致,生产并发用这个模式。
 *    - 顺序多次调用:同 sessionId 不会污染(框架把 generate_response 的调用痕迹从历史压缩掉了),
 *      输出结构稳定但值有随机波动;不同 sessionId 完全隔离。
 *    - 另:required=true 也挡不住"空数组"(亮点出现过 0 条),响应侧要做非空校验。
 */
public class Example6_StructuredOutput {

    public static void main(String[] args) throws Exception {
        // 1. 创建 Agent:和示例 1 完全一样,不需要为结构化输出做任何特殊配置
        ReActAgent agent = ReActAgent.builder()
                .name("review-analyzer")
                .sysPrompt("你是一个影评分析助手。")
                .model(ModelFactory.createGlmChatModel())
                .maxIters(3)
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("structured-output-demo")
                .userId("learner")
                .build();

        String review = """
                《流浪地球3》终于上映了。作为从2019年第一部就开始追这个系列的老影迷,我首映当晚就抢了IMAX的票,\
                散场走出影院那一刻,只想说一句:中国科幻电影这一仗,真的打赢了。

                先说视效。太空电梯的长镜头依旧震撼,但这一部最让我惊艳的是"月球坠落"段落:碎裂的月岩像银色暴雨\
                一样砸向地球的行星发动机阵列,每一块碎片的光影、速度感和撞击的重量感都做得极其扎实,完全看不出\
                "贴图感";数字生命空间那场戏,把数据流做成了可以游泳的星海,美术想象力拉满。杜比音效也值得单独表\
                扬,发动机点火的低频是从座椅底下传上来的,后排有观众直接惊呼出声。

                叙事上,图恒宇的数字生命线与MOSS的暗线在这一部正式交汇,三条时间线虽然跳跃,但每个转折都有铺垫,\
                看到后半段会不断"原来如此",比第二部那种赶场式的剧情推进好太多。图恒宇和刘培强隔着数字世界的一段\
                对手戏,全场安静得能听到呼吸声,吴京终于不再只是"打不死的硬汉",刘德华把偏执与温柔拿捏得恰到好处,\
                新加入的年轻演员也没有拖后腿。

                缺点也要说。第一,结尾稍显仓促,最后那场牺牲的情绪刚被推起来就切走了,像一锅汤大火收汁收急了;第\
                二,部分科学设定经不起细想,行星发动机的燃料逻辑、地月引力变化的连锁反应,科幻硬核党大概率会皱眉\
                ;第三,169分钟的片长对膀胱是场考验,中段至少有十分钟可以压缩,我旁边座的哥们看了三次手机。

                音乐这次换了班底,主题旋律比前作克制,只在两场高潮戏放开了拉,反而更有效果。彩蛋有两个,一个在字\
                幕后,别急着走;另一个藏在开场三分钟,二刷的时候注意看背景里的新闻画面。

                另外提一句映前风波:去年物料泄露那次,郭帆在路演上说"砍掉了整整40分钟的戏",当时我还担心影片会\
                残缺,现在看多虑了——正片的完成度相当高,被砍的部分反而让节奏更紧。据说这次的剧本磨了整整四年,\
                一万多张分镜画稿,这种笨功夫在成片里是看得见的。如果你是第一部入坑的观众,这部几乎是为老影迷准\
                备的情书;如果没看过前两部,强烈建议先补课,否则至少三成的情感共鸣会打折扣。

                最后给个非官方观影指南:适合人群——科幻迷、系列老粉、视效党;不适合——追求严密硬科幻的逻辑党、\
                坐不住的小朋友。排片建议挑工作日晚场,周末IMAX厅的熊孩子浓度显著升高。

                综合评分8.5。强烈推荐IMAX或CINITY厅,普通银幕至少损失三成体验;带纸巾,但别带太多——这一部比第\
                二部克制,眼泪是温的不是烫的。一句话总结:它不完美,但它配得上"中国科幻里程碑"这七个字,我的二\
                刷已经排上了。
                """;
        System.out.println("(测试文本长度: " + review.length() + " 字符,>1000 的长文本压力测试)");

        // ── Part 1(对照):普通 call,自由文本 ──
        // 同样一份影评,让模型自由发挥,每次输出的措辞和结构都不一样,程序没法稳定解析。
        System.out.println(">>> Part 1: 普通 call(自由文本,格式不可控)");
        Msg free = agent.call("用两三句话总结这条影评:\n" + review, ctx).block();
        System.out.println("<<< " + free.getTextContent());

        // ── Part 2:框架内置结构化输出,共享 Agent + 3 线程并发(不同 sessionId)──
        // 验证两件事:1) 共享 Agent 并发调用是否安全、sessionId 之间是否隔离;
        //            2) 同样的输入多次调用,输出是否一致(大模型有随机性,结构相同值未必相同)。
        System.out.println();
        System.out.println(">>> Part 2: 结构化 call(共享 Agent,3 线程并发,不同 sessionId)");
        System.out.println("(框架内部把 MovieReview 转成 JSON Schema,注册成数据接收工具: "
                + ReActAgent.STRUCTURED_OUTPUT_TOOL_NAME + ")");
        long t0 = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<MovieReview>> futures = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                RuntimeContext c = RuntimeContext.builder()
                        .sessionId("concurrent-" + idx).userId("learner").build();
                Msg r = agent.call("分析这条影评:\n" + review, MovieReview.class, c).block();
                return (r != null && r.hasStructuredData()) ? r.getStructuredData(MovieReview.class) : null;
            }));
        }
        for (int i = 0; i < futures.size(); i++) {
            MovieReview m = futures.get(i).get();
            System.out.println("  [线程" + (i + 1) + "] " + oneLine(m));
            validate(m);
        }
        pool.shutdown();
        System.out.println("  (3 次并发总耗时 " + (System.currentTimeMillis() - t0) + " ms,串行约需 3 倍)");

        // ── Part 2b:同 sessionId 顺序调两次 —— 多次调用会不会互相影响? ──
        // 同一个 sessionId 的 Agent 有会话记忆:第二次调用时,模型能看到第一轮的提问
        // (框架会把 generate_response 的工具调用/结果从历史里压缩掉,但用户消息还在)。
        System.out.println();
        System.out.println(">>> Part 2b: 同一 sessionId 顺序调 2 次(验证会话记忆的影响)");
        RuntimeContext sameCtx = RuntimeContext.builder()
                .sessionId("same-session").userId("learner").build();
        for (int round = 1; round <= 2; round++) {
            Msg r = agent.call("分析这条影评:\n" + review, MovieReview.class, sameCtx).block();
            MovieReview m = (r != null && r.hasStructuredData()) ? r.getStructuredData(MovieReview.class) : null;
            System.out.println("  [第" + round + "次] " + oneLine(m));
        }

        // ── Part 2c:定位根因 —— 每线程独立 Agent,但共享同一个 Model 实例 ──
        // 如果这组结果正常,说明污染源是共享 Agent;如果同样出垃圾,说明是共享 Model
        // (流式 SSE 连接被并发请求互相串流)的问题。
        System.out.println();
        System.out.println(">>> Part 2c: 独立 Agent + 共享 Model(定位并发污染根因)");
        io.agentscope.core.model.Model sharedModel = ModelFactory.createGlmChatModel();
        ExecutorService pool2c = Executors.newFixedThreadPool(3);
        List<Future<MovieReview>> f2c = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            f2c.add(pool2c.submit(() -> {
                ReActAgent a = ReActAgent.builder()
                        .name("iso-agent-" + idx)
                        .sysPrompt("你是一个影评分析助手。")
                        .model(sharedModel)
                        .maxIters(3)
                        .build();
                RuntimeContext c = RuntimeContext.builder()
                        .sessionId("iso-" + idx).userId("learner").build();
                Msg r = a.call("分析这条影评:\n" + review, MovieReview.class, c).block();
                return (r != null && r.hasStructuredData()) ? r.getStructuredData(MovieReview.class) : null;
            }));
        }
        for (int i = 0; i < f2c.size(); i++) {
            System.out.println("  [线程" + (i + 1) + "] " + oneLine(f2c.get(i).get()));
        }
        pool2c.shutdown();

        // ── Part 3:手动"数据接收工具"(System Prompt 强制),3 线程并发,每线程独立 Agent ──
        // 手动工具的对象是捕获式的(结果存在工具实例里),并发时每个线程要有自己的工具和 Agent,
        // 共享一个工具实例会互相覆盖——这也是多线程下的一个注意点。
        System.out.println();
        System.out.println(">>> Part 3: 手动数据接收工具(每线程独立 Agent,3 线程并发)");
        ExecutorService pool3 = Executors.newFixedThreadPool(3);
        List<Future<String>> manualFutures = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            manualFutures.add(pool3.submit(() -> {
                ReviewSubmitTool submitTool = new ReviewSubmitTool();
                Toolkit toolkit = new Toolkit();
                toolkit.registerTool(submitTool);
                ReActAgent manualAgent = ReActAgent.builder()
                        .name("manual-structured-" + idx)
                        .sysPrompt("你是影评分析助手。分析用户给出的影评时,必须调用 submit_review_result 工具提交结果,"
                                + "这是唯一的交付方式,禁止用文本直接回答;调用一次即可,参数填完整。")
                        .model(ModelFactory.createGlmChatModel())
                        .toolkit(toolkit)
                        .maxIters(3)
                        .build();
                RuntimeContext manualCtx = RuntimeContext.builder()
                        .sessionId("structured-manual-" + idx).userId("learner").build();
                manualAgent.call("分析这条影评:\n" + review, manualCtx).block();
                return oneLine(submitTool.getCaptured());
            }));
        }
        for (int i = 0; i < manualFutures.size(); i++) {
            try {
                System.out.println("  [线程" + (i + 1) + "] " + manualFutures.get(i).get());
            } catch (Exception e) {
                System.out.println("  [线程" + (i + 1) + "] 捕获失败: " + rootMessage(e));
            }
        }
        pool3.shutdown();
    }

    /** 单行摘要,方便多线程结果横向对比;null 字段用 <null> 标出 */
    private static String oneLine(MovieReview m) {
        if (m == null) {
            return "(未返回结构化数据)";
        }
        int n = m.highlights == null ? 0 : m.highlights.size();
        return "片名=" + (m.movieName == null ? "<null>" : m.movieName)
                + ", 情感=" + (m.sentiment == null ? "<null>" : m.sentiment)
                + ", 评分=" + m.rating
                + ", 推荐=" + m.recommend
                + ", 亮点=" + n + "条" + (n > 5 ? "(超上限!)" : "");
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage();
    }

    private static void validate(MovieReview data) {
        if (data == null) {
            System.out.println("    (业务校验) 无结构化数据,跳过");
            return;
        }
        if (data.rating == null || data.rating < 0 || data.rating > 10) {
            System.out.println("    (业务校验) 评分 " + data.rating + " 越界,这条数据不能直接入库");
        } else {
            System.out.println("    (业务校验) 评分 " + data.rating + " 在 0~10 范围内,数据可信");
        }
    }

    /**
     * 目标数据结构:普通 POJO 即可。
     * AgentScope 用 Schema 生成器把它变成 JSON Schema 发给模型,再用 Jackson 把模型填的参数反序列化回来。
     * 要求:public 字段(或 getter)+ 无参构造函数;字段名即 Schema 属性名。
     * 实测教训:关键字段务必加 @JsonProperty(required = true)——Schema 生成器默认不把字段标进
     * required 数组,长文本输入下模型会跳过"可填可不填"的字段(片名/亮点直接丢)。
     * 注意 required 只解决"有没有";描述文字里的枚举值约定、数量上限对模型没有强制力。
     */
    public static class MovieReview {
        /** 片名(不含书名号) */
        @JsonProperty(value = "movieName", required = true)
        public String movieName;
        /** 整体情感倾向,只能是 POSITIVE / NEGATIVE / NEUTRAL 之一 */
        @JsonProperty(value = "sentiment", required = true)
        public String sentiment;
        /** 影评中给出的评分,0.0~10.0;影评没提分则给 null */
        public Double rating;
        /** 影评是否推荐观看 */
        public boolean recommend;
        /** 影评提到的亮点关键词,2~5 个 */
        @JsonProperty(value = "highlights", required = true)
        public List<String> highlights;

        public MovieReview() {
        }

        public MovieReview(String movieName, String sentiment, Double rating,
                           boolean recommend, List<String> highlights) {
            this.movieName = movieName;
            this.sentiment = sentiment;
            this.rating = rating;
            this.recommend = recommend;
            this.highlights = highlights;
        }
    }

    /**
     * Part 3 的"数据接收工具":参数就是数据本身,方法体只负责捕获,不干别的。
     * 模型发出的工具调用参数是一段 JSON,AgentScope 按 @ToolParam 的名字和类型反序列化成这些入参
     * ——这就是"工具参数 = 由 Schema 约束的 JSON"的落点。
     */
    public static class ReviewSubmitTool {
        private final AtomicReference<MovieReview> captured = new AtomicReference<>();

        @Tool(name = "submit_review_result",
                description = "提交影评分析结果。分析完成后必须调用本工具交付,这是唯一的提交方式。")
        public String submitReviewResult(
                @ToolParam(name = "movie_name", required = true, description = "片名,不含书名号") String movieName,
                @ToolParam(name = "sentiment", required = true, description = "整体情感倾向:POSITIVE/NEGATIVE/NEUTRAL") String sentiment,
                @ToolParam(name = "rating", required = true, description = "评分0.0~10.0,影评未提分则填-1") Double rating,
                @ToolParam(name = "recommend", required = true, description = "是否推荐观看") boolean recommend,
                @ToolParam(name = "highlights", required = true, description = "亮点关键词,2~5个") List<String> highlights) {
            captured.set(new MovieReview(movieName, sentiment, rating, recommend, highlights));
            return "已收到,请结束。";
        }

        /** 取模型提交的结果;模型没调工具就抛错——这正是"System Prompt 强制"写法的固有风险 */
        public MovieReview getCaptured() {
            MovieReview m = captured.get();
            if (m == null) {
                throw new IllegalStateException("""
                        模型没有调用 submit_review_result 提交结果。
                        这就是"System Prompt 强制"写法的风险:约束只是提示词级别,模型可以不调工具直接回文本。
                        要更强的保证,用 Part 2 的框架内置结构化输出,或底层端点支持时的 tool_choice=required / strict 模式。
                        """);
            }
            return m;
        }
    }
}
