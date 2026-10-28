# Example6_StructuredOutput 验证报告

> 对 AgentScope Java 2.0.2 结构化输出（Structured Output）的机制核实与长文本/多线程压力实测记录。
> 测试日期:2026-10-27 ~ 2026-10-28 | 联系上下文:[README.md](README.md) 示例 6

## 1. 验证目标

1. 框架内置结构化输出（`agent.call(问题, Class, ctx)`）的真实实现机制是什么？是否如部分教程所称"tool_choice 服务端强制、绝对合规"？
2. 长文本（>1000 字符）输入下，结构化输出的字段完整性、取值正确性表现如何？
3. 多次调用（顺序/并发）是否影响输出？多线程的正确姿势是什么？

## 2. 测试环境

| 项 | 值 |
|---|---|
| 框架 | AgentScope Java 2.0.2（`agentscope-harness` + `agentscope-extensions-model-openai` + `GLMFormatter`） |
| 模型 | `glm-5.3-flash` @ 智谱开放平台 OpenAI 兼容端点（`stream=true`，temperature 默认） |
| JDK / 构建 | 17 / Maven |
| 输入文本 | 1043 字符电影长评（多段落：视效/叙事/演员/缺点/音乐/幕后/观影指南），见 `Example6_StructuredOutput.java` 的 `review` 变量 |
| 目标结构 | `MovieReview`：片名 / 情感(约定枚举 POSITIVE·NEGATIVE·NEUTRAL) / 评分(0~10) / 是否推荐 / 亮点(约定 2~5 条) |
| 真实 API 调用 | 全程约 30 次 |

## 3. 结论速览

| # | 结论 | 证据章节 |
|---|---|---|
| 1 | 框架内置结构化输出靠"数据接收工具 + 工具描述约束"，**不是** `tool_choice` 强制，更谈不上解码级"绝对合规" | §4 |
| 2 | 长文本下 Schema 默认不标 required → **系统性丢字段**；`@JsonProperty(required=true)` 可修复"有没有" | §5.1 |
| 3 | required 修复后，**描述里的软约束仍全线失守**：枚举变自由文本、数量上限被无视、事实性字段（推荐与否）填反 | §5.2 |
| 4 | 手动数据接收工具（参数全 required + 描述写死取值集）在长文本下三轮全对，稳定性显著优于框架内置路径 | §5 |
| 5 | **并发共享 Agent 或共享 Model 都会劣化输出**（幻觉/丢字段/互相矛盾），框架无并发护栏；每线程独立 Agent+Model 则两轮 6/6 全对 | §6 |
| 6 | 顺序多次调用不互相污染（结构化调用痕迹会被从历史压缩），但值有大模型固有随机波动 | §7 |

## 4. 机制核实（对 agentscope-core 2.0.2 反编译）

**调用路径**：`agent.call(msgs, Class, ctx)` → `doStructuredCall`：

1. `JsonSchemaUtils.generateSchemaFromClass(Class)` 用 victools + Jackson 模块生成 JSON Schema（`ToolSchemaModule(PROPERTY_REQUIRED_FALSE_BY_DEFAULT)`——**字段默认不进 required 数组**）；
2. 若模型声明 `supportsNativeStructuredOutput(WithTools)` → 走 `response_format`（json_schema）原生通道，失败自动 `onErrorResume` 回退；
3. 回退路径 = **工具注入**：把 Schema 包成参数为 `{response: <你的Schema>}` 的工具，注册名为 **`generate_response`**（`ReActAgent.STRUCTURED_OUTPUT_TOOL_NAME` 常量值），工具描述原文：

   > "Generate the final structured response. Call this function when you have all the information needed to provide a complete answer."

4. 模型一旦调用该工具，`PostActingEvent.stopAgent()` 截停 ReAct 循环，参数解包挂到结果 `Msg` 的 metadata（key `_structured_output`），客户端用 `getStructuredData(Class)` 取对象；
5. 调用结束后 `compressStructuredOutputContext` 把 `generate_response` 的调用/结果从会话历史剥离（用户消息保留）。

**三个"传言"的证伪**：

| 传言 | 核实结果 |
|---|---|
| "通过 tool_choice 在服务端强制调用" | 整个结构化调用路径**没有任何 `ToolChoice` 引用**（字节码全文检索为 0） |
| "TOOL_CHOICE / PROMPT 双模式可配" | `StructuredOutputReminder` 枚举在 core/harness/openai 三个 jar 里**零消费者**——1.x 遗留死代码 |
| "System Prompt 强制 → 绝对合规" | 强制手段只是上面那句**工具描述**（提示词级）；"绝对合规"需端点级 strict/response_format，OpenAI 兼容端点默认不传 strict 字段 |

## 5. 实验一：长文本字段完整性与取值正确性（3 轮）

### 5.1 required 之前：系统性丢字段

| 轮次 | Part 2 框架内置 | Part 3 手动工具（参数全 required） |
|---|---|---|
| 第 1 轮 | 片名 **null**，亮点 **null**，情感 `positive`(小写)，评分 8.5 ✓ | 全对：流浪地球3 / POSITIVE / 8.5 / true / 亮点 5 条 |
| 第 2 轮 | 片名 **null**，亮点 **null**，情感 "正面(带保留)"，推荐 **false**(影评明写"强烈推荐") | 全对：POSITIVE / 8.5 / true / 亮点 5 条 |

**根因**：Schema 生成器默认不把字段标进 required 数组；长文本下模型注意力被稀释，"可填可不填"的字段直接跳过。两轮复现，系统性问题。

### 5.2 required 之后：丢字段修复，软约束失守

给 `movieName`/`sentiment`/`highlights` 加 `@JsonProperty(required=true)` 后第 3 轮：

- 片名/亮点**恢复填写**（required 只解决"有没有"）；
- 情感仍返回自由文本 `"正面(克制型好评)"`，无视约定的 POSITIVE/NEGATIVE/NEUTRAL 枚举；
- 亮点无视"2~5 个"说明，**倾倒 30+ 条**分析；
- 推荐仍填 **false**（事实错误）。

**判读**：Schema 能约束"有没有、是什么类型"；约束不了"值属于某枚举、符合事实、数量上限"。后者要么用真正的 Java enum 类型进 Schema，要么响应侧校验 + 重试，要么像 Part 3 那样把取值集合写死在参数描述里（本轮及后续所有轮次 Part 3 枚举零失误）。

## 6. 实验二：多线程并发（3 线程，同一影评，2 轮）

| 并发模式 | 实测结果 |
|---|---|
| ① 共享 Agent（sessionId 各不相同也一样） | **严重劣化**：第 1 轮线程 1 幻觉出影评里不存在的电影 "The Silent Circuit"（评分 3.5）；其余线程字段成片为空/评分 0.0。第 2 轮未崩但亮点 0~2 条、情感中英混杂、推荐值互相矛盾 |
| ② 独立 Agent + 共享 Model | 仍劣化：亮点 14 条(超上限)、评分 null、片名给英文 "The Wandering Earth 3" |
| ③ 独立 Agent + 独立 Model（Part 3 写法） | **两轮 6/6 全对，三条结果完全一致**（POSITIVE / 8.5 / true / 亮点 5 条） |

补充观测：

- ① 的 3 次并发总耗时 129.5 s，相对串行**没有加速**——劣化伴随的不是并行收益，而是纯粹的互相干扰；
- `Builder.checkRunning` 在整个 core 包里**没有任何实现**——共享实例并发不会报错，只会悄悄产出坏数据，比直接崩掉更危险；
- 捕获式的手动工具实例（`ReviewSubmitTool` 内部 `AtomicReference`）同样不能跨线程共享，会互相覆盖，每线程各建一个。

**劣化机理（定性）**：① 是 Agent 的调用中间态（当轮工具注入、事件流、回复聚合）被并发踩踏——与 sessionId 无关，因为踩的不是按 sessionId 隔离的会话记忆，而是调用过程本身；② 说明 `OpenAIChatModel` 的流式传输层对并发请求也未做到完全隔离（疑似 SSE 串流）。

## 7. 顺序多次调用的影响（Part 2b，同 sessionId 连调 2 次）

- **无污染**：框架会把 `generate_response` 调用痕迹从历史压缩掉，第二轮不会被第一轮结果带偏；两轮片名均正确；
- **有波动**：字段值存在随机起伏（情感 `positive`/`正面`、亮点 0~1 条），且出现过评分 null——大模型固有随机性 + 长文本下的注意力问题叠加；
- 不同 sessionId 则完全隔离。

## 8. 生产建议(推荐方案)

**结论:手动数据接收工具模式(Part 3 骨架)+ 每请求独立 Agent/Model + 响应侧校验重试。**
这是全部实测数据指向的最稳组合——Part 3 在长文本、并发、多轮所有场景下零失误(含枚举、数量、事实正确性),而框架内置路径(Part 2)修复 required 之后仍会软约束失守。

### 8.1 结构化交付:手动工具,参数全 required + 取值集合写死

```java
@Tool(name = "submit_review_result", description = "提交影评分析结果,分析完成后必须调用本工具交付")
public String submit(
    @ToolParam(name = "movie_name", required = true, description = "片名") String movieName,
    @ToolParam(name = "sentiment", required = true, description = "取值只能是 POSITIVE、NEGATIVE、NEUTRAL 三者之一") String sentiment,
    @ToolParam(name = "rating", required = true, description = "0.0~10.0 的数字") Double rating,
    @ToolParam(name = "recommend", required = true, description = "true 或 false") boolean recommend,
    @ToolParam(name = "highlights", required = true, description = "2~5 个关键词的数组") List<String> highlights)
```

- 枚举写进参数描述后,实测所有轮次零失误——这是 GLM/DeepSeek 这类没有解码级 strict 的端点上最有效的约束手段;
- System Prompt 再加一句"必须调用该工具提交、禁止文本直接回答";
- 捕获式工具实例(内部 AtomicReference)**每请求各建一个**,绝不能共享。

### 8.2 并发模型:每请求独立 Agent + 独立 Model

```java
ReActAgent agent = ReActAgent.builder()
        .name("extractor-" + requestId)
        .sysPrompt("...")
        .model(ModelFactory.createModel())   // 每请求新建,不共享
        .toolkit(perRequestToolkit)
        .maxIters(2)
        .build();
RuntimeContext ctx = RuntimeContext.builder()
        .sessionId(UUID.randomUUID().toString())  // 无状态抽取用随机 sessionId
        .userId("svc").build();
```

- builder 都是廉价对象,按请求创建没有性能负担;
- 共享 Agent / 共享 Model 两种模式均已实测劣化(§6),且框架不报错、只出坏数据,禁用;
- 注意:独立 Agent 各持独立内存态 StateStore,**同一 sessionId 跨线程/跨 Agent 实例不共享记忆**;抽取类无状态任务用随机 sessionId 即可,确需跨请求续会话要接外部 StateStore。

### 8.3 响应侧校验 + 有限重试(最后一道闸,必须做)

```java
for (int i = 0; i < 2; i++) {            // 最多 2 次尝试
    Result r = extractOnce(text);         // 独立 Agent + 工具捕获
    List<String> errors = validate(r);    // 非空 + 枚举 + 范围 + 业务合理性
    if (errors.isEmpty()) return r;
    log.warn("第{}次结构化输出校验失败: {}", i + 1, errors);
}
throw new ExtractionException();          // 两次都失败不要硬塞入库,进人工队列
```

校验至少三层:

1. **非空**:required 挡不住空数组/空串(亮点出现过 0 条),`highlights.isEmpty()` 之类的检查自己做;
2. **枚举/范围**:sentiment ∈ {POSITIVE, NEGATIVE, NEUTRAL},rating ∈ [0,10];
3. **事实合理性**:如"强烈推荐"的影评 recommend 却是 false,拒收重试(实测框架内置路径填反过)。

### 8.4 参数与监控

| 项 | 建议 | 依据 |
|---|---|---|
| temperature | 0~0.2 | 抽取任务实测同输入值有波动(§7) |
| maxIters | 2 封顶 | 防循环失控;抽取场景一轮就该交卷 |
| 超时 | call 外层加超时 | 流式聚合无内建超时保护 |
| 监控指标 | 字段填充率、校验失败率、重试占比 | 本链路失效模式是**静默劣化**而非报错(checkRunning 无实现),无指标等于裸奔 |

### 8.5 两个补充判断

- **框架内置 `call(问题, Class)` 不是不能用**:内部工具、低风险场景,加上 `@JsonProperty(required=true)` + §8.3 校验兜底也够用;但它的强制力天生弱一层(Schema 默认不标 required、约束只靠一句工具描述),关键数据管线不值得赌;
- **更强的保证在路上**:若底层端点支持解码级 Structured Outputs(如 DeepSeek Responses API 的 `text.format.type="json_schema"`)且框架 `supportsNativeStructuredOutput` 通道打通,那才是比"工具 + 校验"更强的保证——当前 GLM 兼容端点走不到该通道,工具模式即当下最优解。

## 9. 复现方式

```bash
mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example6_StructuredOutput
```

运行序列：Part 1 基线（自由文本）→ Part 2 共享 Agent 3 线程并发 → Part 2b 同 sessionId 顺序 2 次 → Part 2c 独立 Agent + 共享 Model 定位组 → Part 3 独立 Agent + 独立 Model 3 线程并发。单轮约 12 次 API 调用。

## 10. 局限性说明

- 单模型（glm-5.3-flash）、小样本（每场景 2~3 轮），结论是工程判读而非统计显著；
- 大模型输出非确定性，复跑的具体字段值会不同，但"丢字段/软约束失守/并发劣化"三类现象在所有轮次均复现；
- 第二轮并发实验的 Part 2 线程 3 输出因日志窗口截断未采集（不影响该组"至少 2/3 线程劣化"的判读）；
- 机制结论来自 2.0.2 字节码反编译，后续版本可能改变（尤其并发护栏与 required 默认值）。
