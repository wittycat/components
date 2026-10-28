package com.wittycat.agentscope;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 示例 7:DeepSeek 严格结构化输出——长文本压力下 json_schema(解码层硬约束)与 json_object(口头约定)对比。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example7_DeepSeekStructuredOutput
 *
 * 【知识点】(API 行为 2026-10-28 对 DeepSeek 官方文档 + 实际调用核实)
 * 1. 实验设计:用示例 6 那篇 1043 字符长影评(工具通道在它身上翻过车:枚举变自由文本、
 *    亮点倾倒 30+ 条、推荐填反),裸调 DeepSeek Responses API,只换 text.format.type,
 *    验证"解码层硬约束"在长文本下是否依然成立。不经过 AgentScope,排除框架因素。
 * 2. DeepSeek 有两套 OpenAI 兼容 API,结构化能力不同(这是最容易混淆的点):
 *      - Chat Completions(POST /chat/completions):response_format 只支持 text / json_object,
 *        json_object 只保证"合法 JSON",不保证符合 Schema。实测发 json_schema 直接 400:
 *        "This response_format type is unavailable now"。
 *      - Responses(POST /responses,无状态,需重发完整历史):text.format.type 支持
 *        text / json_object / json_schema;type=json_schema 时 name 和 schema 必填——
 *        这就是 DeepSeek 官方文档意义上的"严格结构化输出"(Structured Outputs)。
 * 3. json_schema 的硬约束(本例 Schema 手写,enum/minItems/maxItems/required 全开):
 *    情感只可能落在三个枚举值里,亮点被夹在 2~5 条,必填字段一个不少——这些在示例 6 的
 *    工具通道下只是"描述文字",在 json_schema 下是生成阶段就被执行的合同条款。
 *    sentiment 映射到真正的 Java enum:模型若返回约定外的值,Jackson 反序列化直接失败,
 *    类型系统在最后一层兜底。
 * 4. json_object(对照):只承诺"返回合法 JSON",字段名/类型/取值全凭模型自觉——
 *    这次可能恰好合规,但合同上没有约束力,长文本下尤其赌不得。
 * 5. 响应结构:顶层 status(completed / incomplete / failed,截断看 incomplete_details);
 *    output[] 是数组,deepseek-flash 默认开启思考,所以有两条:reasoning/reasoning_text(思考过程)
 *    + message/output_text(最终回答)。业务 JSON 是 output_text 里的一段"字符串",要二次解析。
 * 6. 思考模式与正确性:官方 API 下含思考的 json_schema 输出实测正常(社区第三方自部署
 *    报告过思考下 json_schema 损坏,那是 vLLM 类自建环境的问题,与官方 API 不可混谈)。
 * 7. AgentScope 接入(本例已不含,实验记录见验证报告 §6/§7):OpenAIChatModel +
 *    ModelFactory.createDeepSeekChatModel() 即可;注意 2.0.2 的原生结构化开关默认是 true,
 *    会先向 Chat Completions 发 json_schema 吃 400 再自动回退,工厂已默认显式关闭。
 * 8. 【严谨性提醒】json_schema 的承诺是"正常完成(status=completed)的输出符合 Schema":
 *      - 值的事实正确性(评分是不是影评真给的)依然要业务校验;
 *      - 截断(status=incomplete, incomplete_details.reason=max_output_tokens)时输出可能不完整;
 *      - 生产建议保留 Schema 校验兜底,防端点行为漂移。
 * 9. 配置:DeepSeek 的 key/base-url/model 放 application-local-deepseek.yml(llm: 段,已被
 *    .gitignore 排除),ModelFactory 统一读取,优先级与 GLM 相同(-D > 环境变量 > local yml > 默认)。
 */
public class Example7_DeepSeekStructuredOutput {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // 与示例 6 完全相同的 1043 字符长影评——工具通道在它身上系统性丢字段/软约束失守,
        // 本例验证 json_schema 硬约束在同一输入上的表现,两组数据可直接横向对比。
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
        System.out.println("(测试文本长度: " + review.length() + " 字符,与示例 6 同一篇长评,可直接横向对比)");

        String model = ModelFactory.llmValue("model-name", "DEEPSEEK_MODEL",
                ModelFactory.DEEPSEEK_DEFAULT_MODEL);
        System.out.println("(模型: " + model + ",端点: " + responsesUrl() + ")");

        // ── Part 1:裸调 Responses API + text.format.type=json_schema —— 严格结构化输出 ──
        System.out.println();
        System.out.println(">>> Part 1: Responses API + json_schema(严格结构化输出)");
        System.out.println("(请求里 text.format = {type: json_schema, name, schema},Schema 手写,enum/minItems/maxItems 全开)");
        ObjectNode req1 = responsesRequest(model, "分析这条影评并按 schema 返回:\n" + review, "json_schema");
        JsonNode resp1 = callResponsesApi(req1);
        String payload1 = extractOutputText(resp1, "Part 1");
        MovieReview m1 = parseAndValidate(payload1, "Part 1");
        System.out.println("  结果: " + oneLine(m1));

        // ── Part 2(对照):Responses API + json_object —— 只保证"合法 JSON",不保证符合 Schema ──
        System.out.println();
        System.out.println(">>> Part 2: Responses API + json_object(对照:口头约定,无强制力)");
        System.out.println("(同样的字段要求,但只有'返回 JSON'一个承诺,字段名/类型/取值全凭模型自觉)");
        ObjectNode req2 = responsesRequest(model,
                "分析这条影评,以 JSON 格式返回,字段:movieName(片名)、sentiment(POSITIVE/NEGATIVE/NEUTRAL)、"
                        + "rating(0-10,未提分填-1)、recommend(是否推荐)、highlights(亮点关键词2-5个)。影评:\n" + review,
                "json_object");
        JsonNode resp2 = callResponsesApi(req2);
        String payload2 = extractOutputText(resp2, "Part 2");
        MovieReview m2 = parseAndValidate(payload2, "Part 2");
        System.out.println("  结果: " + oneLine(m2));
    }

    // ────────────────────────── 裸调 Responses API 的最小实现 ──────────────────────────

    /** DeepSeek Responses 端点:从 base-url 推导(去掉结尾的 /v1,再拼 /responses;/v1 与根路径等价) */
    private static String responsesUrl() {
        String base = ModelFactory.llmValue("base-url", "DEEPSEEK_BASE_URL",
                ModelFactory.DEEPSEEK_DEFAULT_BASE_URL);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1")) {
            base = base.substring(0, base.length() - 3);
        }
        return base + "/responses";
    }

    /** 组装 Responses 请求。formatType: json_schema / json_object / text */
    private static ObjectNode responsesRequest(String model, String input, String formatType) throws Exception {
        ObjectNode req = MAPPER.createObjectNode();
        req.put("model", model);
        req.put("input", input);
        ObjectNode format = req.putObject("text").putObject("format");
        format.put("type", formatType);
        if ("json_schema".equals(formatType)) {
            format.put("name", "movie_review");
            format.set("schema", MAPPER.readTree(schemaJson()));
        }
        req.put("max_output_tokens", 4000);
        return req;
    }

    /**
     * 目标 Schema:和 MovieReview 同构,把约束写死在 Schema 里。
     * 注意 enum / minItems / maxItems / additionalProperties——这些在 Responses API 的
     * json_schema 下是解码层硬约束,而在示例 6 的工具通道下只是"描述文字"。
     */
    private static String schemaJson() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "movieName":  {"type": "string", "description": "片名,不含书名号"},
                    "sentiment":  {"type": "string", "enum": ["POSITIVE", "NEGATIVE", "NEUTRAL"],
                                   "description": "整体情感倾向"},
                    "rating":     {"type": "number", "description": "影评给出的评分0-10,未提分填-1"},
                    "recommend":  {"type": "boolean", "description": "是否推荐观看"},
                    "highlights": {"type": "array", "items": {"type": "string"},
                                   "minItems": 2, "maxItems": 5, "description": "亮点关键词,2-5个"}
                  },
                  "required": ["movieName", "sentiment", "rating", "recommend", "highlights"],
                  "additionalProperties": false
                }
                """;
    }

    /** 发请求;非 200 时把错误体打出来并抛异常(400 的报错信息本身就是知识点) */
    private static JsonNode callResponsesApi(ObjectNode request) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(responsesUrl()))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + ModelFactory.resolveDeepSeekApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                .build();
        HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.out.println("  HTTP " + resp.statusCode() + ": " + resp.body());
            throw new IllegalStateException("Responses API 调用失败: HTTP " + resp.statusCode());
        }
        return MAPPER.readTree(resp.body());
    }

    /**
     * 从 Responses 响应里取出业务 JSON 字符串。
     * 结构:output[] → {type: reasoning|message} → content[] → {type: reasoning_text|output_text, text}。
     * deepseek-flash 默认带思考:reasoning 条目是过程,业务 JSON 在 message/output_text 的 text 里。
     */
    private static String extractOutputText(JsonNode root, String label) {
        String status = root.path("status").asText("(缺失)");
        if (!"completed".equals(status)) {
            JsonNode incomplete = root.path("incomplete_details");
            System.out.println("  [" + label + "] status=" + status
                    + (incomplete.isObject() ? ", incomplete_details=" + incomplete.toString() : "")
                    + " —— 输出可能被截断,不要直接解析");
        }
        String reasoning = null;
        String payload = null;
        for (JsonNode item : root.path("output")) {
            for (JsonNode c : item.path("content")) {
                String type = c.path("type").asText();
                if ("reasoning_text".equals(type)) {
                    reasoning = c.path("text").asText();
                } else if ("output_text".equals(type)) {
                    payload = c.path("text").asText();
                }
            }
        }
        if (reasoning != null) {
            String head = reasoning.replace('\n', ' ');
            if (head.length() > 80) {
                head = head.substring(0, 80) + "...";
            }
            System.out.println("  [思考] " + head);
        }
        System.out.println("  [输出原文] " + (payload == null ? "<无 output_text>" : payload));
        return payload;
    }

    /**
     * JSON 字符串 → POJO(二次解析)+ 业务校验。
     * sentiment 是真正的 Java enum:Part 1 有 Schema 硬约束必然是三个合法值;
     * Part 2 若模型返回"正面"这类自由文本,Jackson 反序列化直接失败——这就是两条通道的差距。
     */
    private static MovieReview parseAndValidate(String payload, String label) {
        if (payload == null || payload.isBlank()) {
            System.out.println("    (" + label + " 校验) 没有 output_text,无法解析");
            return null;
        }
        MovieReview m;
        try {
            m = MAPPER.readValue(payload, MovieReview.class);
        } catch (Exception e) {
            System.out.println("    (" + label + " 校验) JSON 解析/反序列化失败: " + rootMessage(e));
            return null;
        }
        if (m.rating == null || m.rating < -1 || m.rating > 10) {
            System.out.println("    (" + label + " 校验) 评分 " + m.rating + " 越界,不能直接入库");
        }
        int n = m.highlights == null ? 0 : m.highlights.size();
        if (n < 2 || n > 5) {
            System.out.println("    (" + label + " 校验) 亮点 " + n + " 条,超出 2~5 约定");
        }
        return m;
    }

    /** 单行摘要,方便两个 Part 横向对比;null 字段用 <null> 标出 */
    private static String oneLine(MovieReview m) {
        if (m == null) {
            return "(未拿到结构化数据)";
        }
        int n = m.highlights == null ? 0 : m.highlights.size();
        return "片名=" + (m.movieName == null ? "<null>" : m.movieName)
                + ", 情感=" + (m.sentiment == null ? "<null>" : m.sentiment)
                + ", 评分=" + m.rating
                + ", 推荐=" + m.recommend
                + ", 亮点=" + n + "条" + (n < 2 || n > 5 ? "(超范围!)" : "");
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage();
    }

    /**
     * 情感枚举:真正的 enum 类型,而不是"描述里写死的字符串约定"。
     * Jackson 自动把 "POSITIVE" 映射到枚举;遇到非法值直接抛异常——
     * 反序列化这一层就挡住了自由文本,这正是 enum 进 Schema 的价值。
     */
    public enum Sentiment {
        POSITIVE, NEGATIVE, NEUTRAL
    }

    /**
     * 目标数据结构:与示例 6 的 MovieReview 同构,区别是 sentiment 用真正的枚举类型。
     * 普通 POJO:public 字段 + 无参构造函数;@JsonProperty 给字段标注释供 Schema 生成器使用。
     */
    public static class MovieReview {
        /** 片名(不含书名号) */
        @JsonProperty(value = "movieName", required = true)
        public String movieName;
        /** 整体情感倾向 */
        @JsonProperty(value = "sentiment", required = true)
        public Sentiment sentiment;
        /** 影评中给出的评分,0.0~10.0;影评没提分则给 -1 */
        public Double rating;
        /** 影评是否推荐观看 */
        public boolean recommend;
        /** 影评提到的亮点关键词,2~5 个 */
        @JsonProperty(value = "highlights", required = true)
        public List<String> highlights;

        public MovieReview() {
        }
    }
}
