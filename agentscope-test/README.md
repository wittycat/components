# agentscope-test —— AgentScope Java 学习示例

[AgentScope](https://github.com/agentscope-ai/agentscope-java) 是阿里 ModelScope 团队开源的 Agent 框架(Java 版,要求 JDK 17+)。本模块包含 7 个**递进式**示例,每个都是独立可运行的 main 类,注释里写清了每个知识点。

> **不涉及前端**:学习核心全在后端(模型接入、ReAct 循环、工具调用、会话记忆、流式接口),用控制台和 curl 验证即可。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置智谱 API Key**(推荐方式):编辑 `src/main/resources/application-local.yml`(该文件已被 `.gitignore` 排除,不会提交),填入你的 key(就是 `ai-knowledge-base-test/backend` 里那个):

```yaml
glm:
  api-key: 你的key
  base-url: https://open.bigmodel.cn/api/coding/paas/v4
  model: glm-5.3-flash
```

模型走智谱的 OpenAI 兼容协议,零新增注册。

**可选:配置 DeepSeek**(示例 7 使用):编辑 `src/main/resources/application-local-deepseek.yml`(同样被 `.gitignore` 排除):

```yaml
llm:
  model-name: deepseek-flash
  base-url: https://api.deepseek.com/v1
  api-key: 你的key
```

配置读取优先级(在 `ModelFactory` 里实现,所有示例共用同一条路径):

```
-Dglm.xxx / -Dllm.xxx 系统属性  >  GLM_API_KEY / DEEPSEEK_API_KEY 等环境变量  >  application-local(-deepseek).yml  >  application.yml(默认值)
```

> 提醒:`application-local.yml` 不会进 git,但会被打进 jar 包——不要把带 key 的 jar 发布出去。

## 七个示例(按顺序学)

| # | 类 | 学什么 | 运行命令 |
|---|---|---|---|
| 1 | `Example1_QuickStart` | 创建 Agent、单次调用、拿到回复 | `mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example1_QuickStart` |
| 2 | `Example2_ToolCall` | 自定义工具 + ReAct 循环事件流 | 把 mainClass 换成 `...Example2_ToolCall` |
| 3 | `Example3_Memory` | 多轮会话记忆、会话隔离、清空记忆 | 把 mainClass 换成 `...Example3_Memory` |
| 4 | `Example4_StreamingRest` | Spring Boot + SSE 流式聊天接口 | `mvn -q spring-boot:run` |
| 5 | `Example5_MultiAgent` | 多智能体:编排式流水线 + 委托式(Agent-as-Tool)协作 | 把 mainClass 换成 `...Example5_MultiAgent` |
| 6 | `Example6_StructuredOutput` | 结构化输出:框架内置 `call(问题, Class)` + 手动"数据接收工具"两种写法 | 把 mainClass 换成 `...Example6_StructuredOutput` |
| 7 | `Example7_DeepSeekStructuredOutput` | DeepSeek Responses API:json_schema 严格结构化输出 vs json_object,长文本对照示例 6 | 把 mainClass 换成 `...Example7_DeepSeekStructuredOutput` |

### 示例 2 预期输出(重点看 ReAct 循环)

问"北京和上海今天哪个城市温度更高?高多少度?",会依次看到:

```
[思考] ...(模型推理要查两个城市)
>>> 模型决定调用工具: get_weather
    [工具执行] get_weather(city=北京)   ← AgentScope 真正执行你的 Java 方法
    [工具执行] get_weather(city=上海)
[思考] ...上海 31℃、北京 26℃,该算差值了
>>> 模型决定调用工具: subtract
    [工具执行] subtract(31.0 - 26.0)
--- 最终回答: 上海温度更高,高 5℃
```

### 示例 3 预期输出(重点看记忆)

同一个 sessionId:第二轮能答出第一轮告诉它的名字;**换 sessionId 后答不出**(会话隔离);`clearContext()` 后同样"失忆"。

### 示例 4 测试命令(SSE 流式)

```bash
# -N 关闭 curl 缓冲,才能看到流式效果;sessionId 传相同值可实现多轮对话
curl -N -X POST localhost:8081/api/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"message":"上海今天多少度?","sessionId":"s1"}'

# 追问(同 sessionId,Agent 记得上一轮):
curl -N -X POST localhost:8081/api/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"message":"我刚才问的是哪个城市?","sessionId":"s1"}'
```

SSE 返回格式(每行一个增量帧,`data:` 是 SSE 协议前缀):

```
data:
data:[调用工具: get_weather]
data:上海
data:今天
data:多云
...
data:[[DONE]]
```

### 示例 5 预期输出(重点看"谁在决定流程")

任务"交付一个回文判断工具方法",三位 Agent 分工:`algo-expert`(只讲思路)、`code-expert`(只写代码)、`tech-lead`(只协调)。

- **Part 1 编排式**:Java 代码当导演——先调 `algo-expert` 拿思路,再把思路喂给 `code-expert` 拿代码,流程写死在 main() 里。
- **Part 2 委托式**:`tech-lead` 把两位专员"包成工具"(`consult_algo_expert` / `consult_code_expert`),**模型自己决定**先问谁、问什么、何时收工:

```
(tech-lead 决定调用工具: consult_algo_expert)
>>> [tech-lead 委托] 算法专员: ...请给出算法思路、复杂度分析,以及需要考虑的边界情况...
(tech-lead 决定调用工具: consult_code_expert)
>>> [tech-lead 委托] 编码专员: 按以下算法思路实现 Java 工具方法...   ← 它自己把思路转交给了编码专员
--- tech-lead 最终交付: 思路要点 + 代码
```

委托式就是把 Agent 包成另一个 Agent 的工具(AgentScope 官方 `SubAgentTool` 的底层原理),这是裸调单次 API 做不到的事。

### 示例 6 预期输出(重点看"格式可控")

同一个 Agent、同一份影评,三个部分对比:

- **Part 1** 普通 `call` 返回自由文本,每次都不一样,程序没法解析;
- **Part 2** 多传一个 `MovieReview.class`,直接拿到反序列化好的 Java 对象;
- **Part 3** 手动"数据接收工具"写法:自己注册一个 `@Tool` 当答题卡,在 System Prompt 里强制模型必须调它交卷,工具方法入参就是模型填的表。

```
>>> Part 2: 结构化 call(目标类型 MovieReview.class)
(框架内部把 MovieReview 转成 JSON Schema,注册成数据接收工具: generate_response)
    片名     : 流浪地球3
    情感倾向 : POSITIVE
    评分     : 8.5
    是否推荐 : true
    亮点     : [特效, 太空电梯, 剧情扎实, IMAX]
    (业务校验) 评分 8.5 在 0~10 范围内,数据可信
>>> Part 3: 手动数据接收工具(System Prompt 强制调用)
<<< 工具参数捕获成功:
    ...
```

原理(对 2.0.2 反编译核实):Part 2 由框架把目标 Class 生成 JSON Schema 注册成 `generate_response` 数据接收工具,工具描述约束模型"拿到全部信息后必须调用",模型一旦调用,ReAct 循环立即截停,工具参数解包成结果对象;若模型声明支持原生结构化输出则走 response_format 通道,失败自动回退。Part 3 的强制只是提示词级别,模型可能不调工具直接回文本(示例里 `getCaptured()` 会抛错兜底)。注意:两种写法都只保证"结果以工具参数 JSON 的形态交付",字段值仍是模型生成的,入库前要做业务校验。

### 示例 6 长文本实测(1043 字符影评,GLM 跑三轮)

| 现象 | Part 2 框架内置 | Part 3 手动工具(参数全 required) |
|---|---|---|
| 丢字段 | 两轮系统性丢片名/亮点(null)——Schema 默认不标 required,长文本下模型跳过可选字段 | 三轮全字段完整 |
| 加 `@JsonProperty(required=true)` 后 | 丢字段修复 | 不需要 |
| 枚举约定(POSITIVE/NEGATIVE/NEUTRAL) | 仍返回自由文本"正面(带保留)""正面(克制型好评)" | 三轮都守约(参数描述里写死了枚举) |
| 数量上限("亮点 2~5 个") | 无视,倾倒 30+ 条 | 三轮都在 5 条左右 |
| 事实正确性 | 把"强烈推荐"的影评填成 `recommend=false` | 三轮全对 |

结论:Schema 能约束"有没有、什么类型",约束不了"值属于某枚举、符合事实"。生产上要么把枚举做成真正的 Java enum 类型进 Schema、响应侧校验加重试,要么像 Part 3 一样每个参数都 required 且描述里写死取值集合。

### 示例 6 多线程实测(3 线程并发同一影评,两轮)

| 并发模式 | 结果 |
|---|---|
| 共享 Agent(即使 sessionId 不同) | 严重劣化:一轮某线程幻觉出不存在的电影"The Silent Circuit"(评分3.5);另一轮亮点 0~2 条、情感中英混杂、推荐值互相矛盾 |
| 独立 Agent + 共享 Model | 仍劣化:亮点 14 条超上限、评分 null、片名给英文 |
| 独立 Agent + 独立 Model(Part 3 写法) | **两轮 6/6 全对**,三条结果完全一致 |
| 同 sessionId 顺序调 2 次(Part 2b) | 无污染,结构稳定、值有随机波动 |

多线程结论:

1. **AgentScope 2.0.2 没有并发护栏**——`Builder.checkRunning` 在整个 core 里没有任何实现,共享 Agent/Model 并发不会报错,只会悄悄产出劣化结果,比直接崩掉更危险。
2. **并发正确姿势:每线程独立 Agent + 独立 Model**(Agent 很轻,按需创建即可,Part 3 就是这个写法)。
3. **多次调用的影响**:顺序调用不会互相污染(框架会把 `generate_response` 的调用痕迹从会话历史压缩掉,只留用户消息),但大模型有随机性,同一输入的值会波动;要稳定可比的抽取结果,调低 temperature + 响应侧校验。
4. `required=true` 也挡不住"空数组"(亮点出现过 0 条),非空约束要自己做。

> 完整测试数据、机制反编译细节与判读见 [Example6_StructuredOutput_验证报告.md](Example6_StructuredOutput_验证报告.md)。

### 示例 7 预期输出(DeepSeek:严格结构化输出,长文本对照示例 6)

DeepSeek 的严格结构化输出走 Responses API(`POST /responses`):`text.format.type` 支持 `text` / `json_object` / `json_schema`,`json_schema` 时 `name`+`schema` 必填,正常完成(`status=completed`)的输出**解码层符合 Schema**;`json_object` 只保证"合法 JSON"。

示例用与示例 6 **同一篇 1043 字符长影评**裸调 Responses API(不经过 AgentScope),只换 `text.format.type`,两轮实测(2026-10-28,`deepseek-flash`):

- **Part 1** `json_schema`:**两轮全对**——片名/POSITIVE/8.5/推荐 true/亮点恰好 5 条,enum、`minItems/maxItems`、必填全部被严格遵守。对比示例 6 同一篇影评在 GLM 工具通道的表现(丢字段、枚举变自由文本、亮点 30+ 条、推荐填反),这就是"解码层硬约束"与"工具描述软约束"的差距;`sentiment` 是真正的 Java enum,模型若返回约定外的值,Jackson 反序列化直接失败,类型系统兜底;
- **Part 2** `json_object` 对照:两轮也恰好合规——但那是模型自觉而非合同条款,长文本下赌不得,业务校验必须兜底。

```
>>> Part 1: Responses API + json_schema(严格结构化输出)
  [思考] The user wants JSON output per schema.  Movie: 流浪地球3. Sentiment: POSITIVE...
  [输出原文] {"movieName": "流浪地球3", "sentiment": "POSITIVE", "rating": 8.5, "recommend": true,
              "highlights": ["月球坠落段落的视效", "IMAX与杜比音效的沉浸体验", ...]}   ← 恰好 5 条
  结果: 片名=流浪地球3, 情感=POSITIVE, 评分=8.5, 推荐=true, 亮点=5条
```

响应结构注意:`deepseek-flash` 默认带思考,`output[]` 里有 `reasoning/reasoning_text` + `message/output_text` 两种条目,业务 JSON 是 `output_text` 里的**字符串**,要二次解析;截断看 `status=incomplete` + `incomplete_details`。

> 严谨性提醒:`json_schema` 的承诺是"正常完成(`status=completed`)的输出符合 Schema"——值的事实正确性仍要业务校验,截断(`max_output_tokens`)可能不完整。社区有第三方自部署 DeepSeek V4 在思考模式下 `json_schema` 输出损坏的报告,官方 API 实测(含思考)正常,生产建议保留 Schema 校验兜底。
>
> 完整测试数据与判读见 [Example7_DeepSeekStructuredOutput_验证报告.md](Example7_DeepSeekStructuredOutput_验证报告.md)。

## 核心概念速查

```
ChatModel(模型)        OpenAIChatModel + GLMFormatter → 智谱 GLM(OpenAI 兼容协议)
                        OpenAIChatModel + DeepSeekFormatter → DeepSeek(示例 7)
     ↓ model(...)
ReActAgent(Agent)      推理-行动循环;builder:name / sysPrompt / model / toolkit / maxIters
     ↓ 持有
Toolkit(工具箱)        registerTool(普通 Java 对象),@Tool 方法自动变成模型可调用的工具
     ↓ 运行时
RuntimeContext(上下文) sessionId 决定记忆归属;同一 sessionId 多次 call 自动带上历史
     ↓ 输出
Mono<Msg> / Flux<AgentEvent>   阻塞拿结果用 call().block();流式观测用 streamEvents()
```

- **ReAct 模式**:Reasoning(模型想)→ Acting(调工具)→ 把工具结果回填给模型 → 循环,直到产出最终答案。示例 2 的事件流就是全过程。
- **记忆的本质**:大模型无状态,"记得"=每次请求把历史消息一起发过去。Agent 按 sessionId 帮你存取(示例 3)。
- **事件流**:`io.agentscope.core.event.AgentEventType` 共 30+ 种事件(文本增量/思考增量/工具调用/工具结果...),做前端界面或监控时按需消费(示例 4 只用了 3 种)。

## 目录结构

```
agentscope-test/
├── pom.xml                        # agentscope-harness 2.0.2 + openai 扩展 + spring-boot-starter-web
└── src/main/resources/
    ├── application.yml            # 默认配置(可提交 git;真实 key 别放这里)
    ├── application-local.yml      # 本地私密配置(gitignore,放 GLM api-key)
    └── application-local-deepseek.yml # 本地私密配置(gitignore,放 DeepSeek api-key,llm: 段)
└── src/main/java/com/wittycat/agentscope/
    ├── ModelFactory.java          # 模型工厂:GLM/DeepSeek 接入 + yml 配置加载
    ├── Example1_QuickStart.java   # ①最小 Agent
    ├── Example2_ToolCall.java     # ②工具调用与 ReAct 循环
    ├── Example3_Memory.java       # ③多轮会话记忆
    ├── Example4_StreamingRest.java# ④Spring Boot 启动类(端口 8081)
    ├── Example5_MultiAgent.java   # ⑤多智能体:编排式 + 委托式协作
    ├── Example6_StructuredOutput.java # ⑥结构化输出:call(问题, Class) 直接拿 Java 对象
    ├── Example7_DeepSeekStructuredOutput.java # ⑦DeepSeek:Responses API json_schema vs json_object(长文本)
    └── web/AgentChatController.java # POST /api/agent/chat(SSE 流式)
```

## 常见问题

- **报"未找到智谱 API Key"**:`application-local.yml` 里没填有效 key(还是占位 `demo`),见"环境准备"。报"未找到 DeepSeek API Key"同理,查 `application-local-deepseek.yml`。
- **想换模型**:改 `application-local.yml` 里的 `glm.model`/`glm.base-url` 即可。DeepSeek 配置读 `llm.*`(`application-local-deepseek.yml`),要接 AgentScope 用 `ModelFactory.createDeepSeekChatModel()`(原生开关与回退机制的注意点见该方法的源码注释);其他 OpenAI 兼容服务(Kimi/vLLM 本地模型)同样只改这两项;通义千问引 `agentscope-extensions-model-dashscope` 用 `DashScopeChatModel`。
- **DeepSeek 结构化输出报 400 `This response_format type is unavailable now`**:向 Chat Completions 发了 `response_format: json_schema`——该端点只支持 `text`/`json_object`,严格 Schema 要走 Responses API 的 `text.format`(示例 7)。AgentScope 侧用 `createDeepSeekChatModel()`(默认已关原生开关)则不会遇到。
- **依赖说明**:`agentscope-harness` 是官方推荐入口(内置 workspace/记忆管理,传递引入 `agentscope-core` 的 `ReActAgent`);本示例只用到了它的核心 Agent 能力。
- **版本**:AgentScope 2.x 与 1.x API 不兼容,本模块锁定 `2.0.2`。官方文档:<https://java.agentscope.io>
