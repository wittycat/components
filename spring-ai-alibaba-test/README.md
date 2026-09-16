# spring-ai-alibaba-test —— Spring AI Alibaba 学习示例

[Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) 是阿里巴巴在 **Spring AI 之上**的扩展框架(不是替代品)。本模块锁定 **框架 1.1.2.3 + 模型接入扩展 1.1.2.1**(配套 Spring AI 1.1.2 / Spring Boot 3.5.x / JDK 17+),与父工程的 Boot 3.5.3 兼容。

> **学习主线**:前置知识全在隔壁 `spring-ai-test`(ChatClient/Advisor/@Tool/entity 那一套,两边 API 完全通用)。本模块聚焦 Spring AI Alibaba **多出来**的东西:①DashScope(通义)原生接入 → ②Graph 流程编排(对标 LangGraph) → ③ReactAgent 智能体抽象,共 5 个 CLI 示例 + 1 个 Web SSE 接口。全部示例的运行前提:示例 3 不需要 Key,其余需要一个[阿里云百炼](https://bailian.console.aliyun.com) API Key。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置 DashScope API Key**(示例 1/2/4/5/6 需要):编辑 `src/main/resources/application-local.yml`(已被 `.gitignore` 排除),填入百炼控制台创建的 key:

```yaml
dashscope:
  api-key: sk-你的key
  # 可选:不配就用默认的 qwen-plus;换 qwen-max/qwen-turbo/qwen3 系列只改这里
  # model: qwen-plus
```

3. **Maven 仓库**:直接用全局 `~/.m2/settings.xml`(镜像到私有 Nexus)即可,下文命令不带额外参数。

与 `spring-ai-test` 的配置差异直观点说:**那边**用 OpenAI 兼容端点连智谱,base-url/completions-path 都要手动对齐;**这边** starter 走 DashScope 原生协议,端点默认就对,必填的只有 api-key 和 model。

## 五个示例 + 1 个 Web 接口(按顺序学)

| # | 类 | 学什么 | 是否需要 API Key | 运行命令 |
|---|---|---|---|---|
| 1 | `Example1_QuickStart` | 最小对话:换成 DashScope 后代码零变化 | 需要 | `mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springaialibaba.Example1_QuickStart` |
| 2 | `Example2_QwenThinking` | Qwen 思考模式:enableThinking + reasoningContent | 需要 | 同上,换类名 |
| 3 | `Example3_StateGraph` | Graph 纯状态机:节点/条件边/状态合并策略/Mermaid | **不需要** | 同上,换类名 |
| 4 | `Example4_GraphRouting` | LLM 节点 + 条件路由:意图分类分流工作流 | 需要 | 同上,换类名 |
| 5 | `Example5_ReactAgent` | ReactAgent:工具 + 结构化输出 + threadId 记忆 | 需要 | 同上,换类名 |
| 6 | `web.WebApplication` | Spring Boot + Graph 按节点 SSE 流式(端口 8085) | 需要 | `mvn -q spring-boot:run` |

### 示例 3 预期输出(重点看"条件边分流")

```
轨迹:   [normalize:清洗完成, classify:技术反馈, format:报告已生成]   ← 正常输入走完全程
轨迹:   [normalize:清洗完成]                                          ← 空输入:条件边直接送到 END,后面节点不执行
```

图还能一键导出 Mermaid 流程图(`getGraph(Type.MERMAID)`),粘到 [mermaid.live](https://mermaid.live) 即可可视化——"代码即文档"。

### 示例 5 预期输出(重点看两件事)

```
第 1 轮: 我的工单 T-1024 现在处理到哪一步了?严重吗?
>> [工具被调用] getTicketStatus(T-1024) —— 这行是你的 Java 代码执行的
Agent 原始输出(JSON): {"title":"生产环境登录接口 5xx","severity":"P1","advice":"..."}
第 2 轮(考验记忆,没重复工单号): 针对这个工单的问题,给我一句话处理建议
>> [工具被调用] searchDocs(登录5xx) —— 模型记得上一轮查过什么,直接接话
```

- `outputType(TroubleReport.class)` 让 Agent 最终只吐 JSON,Jackson 一行反序列化成 record;
- 两次 `call` 传同一个 `threadId`,`MemorySaver` 保住了会话状态——没有手动拼历史。

### 示例 6(SSE,按"节点"而非"token"推送)

```bash
mvn -q spring-boot:run
# 另开终端;-N 关闭 curl 缓冲;中文参数需 URL 编码
curl -N "localhost:8085/support/stream?message=%E8%B4%A6%E5%8D%95%E5%A4%9A%E6%89%A3%E4%BA%869.9%E5%85%83%E6%80%8E%E4%B9%88%E9%80%80%E6%AC%BE"
```

每完成一个节点推一帧,能直观看到工作流的推进:`event:classify` → `event:billing_answer`(带完整回答)——与 `spring-ai-test` 示例 9 的 token 粒度流式互补,工作流场景关心"走到哪一步了"。

## 核心概念速查:Graph 五要素

```
StateGraph(图的定义:addNode/addEdge/addConditionalEdges,纯 Java 不依赖 Spring)
        │ compile()
CompiledGraph(可执行:invoke() 同步 / stream() 按节点流式)
        │
        ├── NodeAction      节点 = (state) -> Map 增量;调 LLM 就是在里面写 ChatClient(示例 4)
        ├── EdgeAction      条件边 = (state) -> 下一节点名;返回 map 的 key 或 END
        ├── OverAllState    全局共享状态;同名字段按 KeyStrategy 合并:
        │                       ReplaceStrategy 覆盖 / AppendStrategy 追加成 List(轨迹日志)
        └── RunnableConfig  threadId 标识会话 + MemorySaver 存档 → 多轮记忆/断点续跑(示例 5)

ReactAgent(agent-framework) = 帮你把"ReAct 循环"组装成上面这张图的糖:
        builder().model().methodTools().systemPrompt().outputType().saver() → agent.call(问题, threadId)
```

- **Graph vs Advisor**(两者不冲突):Advisor 是"单次请求管线上的拦截器"(记忆注入、RAG 检索,`spring-ai-test` 学过);Graph 是"多步流程的调度器"(分支/循环/并行/断点)。单次增强用 Advisor,流程编排用 Graph。
- **ReactAgent vs ChatClient.tools()**:`.tools()` 只是"带工具的一次请求";ReactAgent 在其上多了 Agent 生命周期——会话存档、输出类型约束、Hook/Interceptor 扩展点、天然流式事件,以及多 Agent 组合(见下文特性说明)。

## 区别于 Spring AI 的特性说明(重点)

先给结论:**Spring AI Alibaba = Spring AI(地基,全部保留)+ 四层增量**。凡是不带 `com.alibaba` 包的代码(ChatClient/Advisor/@Tool/entity/VectorStore),两边一模一样,学了不白学。

### 1. 模型接入层:DashScope 原生协议 + 全家桶

| | Spring AI(spring-ai-test 的做法) | Spring AI Alibaba(本模块) |
|---|---|---|
| 协议 | OpenAI 兼容端点直连 | DashScope 原生 API(`spring.ai.dashscope.*`) |
| 接线成本 | base-url/completions-path 手动对齐 | 端点默认即对,必填仅 api-key/model |
| 厂商特有参数 | 拿不到 | `DashScopeChatOptions` 全暴露:思考模式(示例 2)、联网搜索 enableSearch、repetitionPenalty 等 |
| 模型覆盖 | chat/embedding(取决于厂商兼容端点) | chat/embedding(text-embedding-v4)/**rerank**(gte-rerank)/图像/语音合成/语音识别/多模态 qwen-vl 全家桶 |

代价:用了 `DashScopeChatOptions` 这类原生类型,代码就绑定了该模型实现(示例 2 里也注释了)。反过来说,直连兼容端点换厂商只改配置,但永远拿不到上面这些能力——鱼与熊掌按需选。

### 2. Graph 编排层(spring-ai-alibaba-graph-core)——最大的独有能力

对标 Python 的 LangGraph(Java 生态此前没有对等物,`langgraph4j-test` 模块可对照着学)。Spring AI 原生**没有任何流程编排抽象**,多步工作流只能自己 if/else。

提供:节点/边/条件边/并行、全局状态与合并策略、`stream()` 节点粒度流式、`MemorySaver`/数据库 CheckPointer 断点续跑、`interruptBefore` 人工介入(human-in-the-loop)、子图(一张图当另一张图的节点)、`getGraph()` 导出 Mermaid/PlantUML 流程图。

### 3. Agent 层(spring-ai-alibaba-agent-framework)——1.1.x 主打

- **ReactAgent**:一个 builder 把模型+工具+提示词+输出类型+存档组装成 Agent 对象(示例 5),比裸用 ChatClient 少写大量胶水代码;
- **多 Agent 模式**:多个 ReactAgent 组合成 Supervisor(主管分发)/Routing(路由)/并行子 Agent 等拓扑,每个 Agent 就是一张子图;
- **A2A**(Agent-to-Agent 协议):把本地 Agent 暴露成 A2A 服务,配合 Nacos 做注册发现,跨应用协作(`spring-ai-alibaba-starter-a2a-nacos`);
- **Hook/Interceptor**:工具调用审批(InterruptionHook 人工介入工具执行)、token 计数(TokenCounter)等 Agent 级扩展点。

### 4. 微服务生态层(阿里系中间件集成)

- **Nacos 动态 Prompt**:prompt 模板放 Nacos 配置中心,改配置秒级热生效,不用发版(依赖 `spring-ai-alibaba-starter-nacos-prompt`,需要 Nacos 环境,本模块未演示);
- **Nacos MCP 注册中心**:MCP server/client 的注册与发现(`spring-ai-alibaba-starter-nacos-mcp-client/server`);
- **可观测**:starter 接阿里云 ARMS,Graph 每一步的执行轨迹可视化(`spring-ai-alibaba-starter-graph-observation` + Admin/Studio)。

### 5. 工具链层

- **Agent Studio / spring-ai-alibaba-admin**:可视化开发、调试、评测 Agent 的管理台;
- **JManus**:阿里开源的 Java 版通用智能体(OpenManus 思路),PLAN-ACT 模式的完整应用,是 agent-framework 的"官方大型示例";
- **开箱即用 starter 合集**:文档解析器(PDF/Word/Tika)、社区工具调用(NL2SQL、高德天气等)、沙箱执行等。

### 一张表总结"什么需求选什么"

| 需求 | 用 Spring AI 就够 | 需要Spring AI Alibaba |
|---|---|---|
| 单轮/多轮对话、RAG、工具调用 | ✅(spring-ai-test 全覆盖) | — |
| 用通义千问 + 思考模式/联网搜索/多模态/语音 | OpenAI 兼容端点勉强能聊 chat | ✅ 原生全家桶 |
| 多步工作流:分类→分支→汇总、循环重试、并行 | 只能业务代码 if/else | ✅ StateGraph |
| Agent 需要会话存档/输出约束/审批/多 Agent 协作 | 需自己搭脚手架 | ✅ ReactAgent/A2A |
| prompt 热更新、MCP 注册发现、阿里云可观测 | — | ✅ Nacos/ARMS 集成 |

### 版本与依赖的坑(2026-09 现状)

- **双 BOM**:框架(graph/agent-framework)与模型接入(DashScope starter)拆在两个仓库、两条版本线(1.1.2.3 / 1.1.2.1),本模块 pom 里分别 import 了 `spring-ai-alibaba-bom` 和 `spring-ai-alibaba-extensions-bom`——只 import 前者时 dashscope starter 会缺版本号(官方 issue #4030);
- **版本锁定**:Spring AI Alibaba 1.1.2.x 编译于 Spring AI 1.1.2,本模块**不要**再 import 更高版本的 `spring-ai-bom`(隔壁 spring-ai-test 用的 1.1.8 是它独立升级的,别抄过来);2.0.x 线面向 Spring Boot 4,等父工程升级再说;
- 依赖全在 Maven Central,无需额外配阿里仓库。

## 与 spring-ai-test 对照着学(API 互通对照)

| 概念 | spring-ai-test(Spring AI) | 本模块(Spring AI Alibaba) |
|---|---|---|
| 模型 starter | `spring-ai-starter-model-openai`(OpenAI 兼容) | `spring-ai-alibaba-starter-dashscope`(原生) |
| 配置前缀 | `spring.ai.openai.*` | `spring.ai.dashscope.*` |
| 一次对话 | `ChatClient.prompt().user().call().content()` | **完全相同**(示例 1) |
| 工具注解 | `@Tool`(org.springframework.ai) | **完全相同**(示例 5) |
| 结构化输出 | `.call().entity(类型)` | **完全相同**(示例 4 分类节点) |
| 会话记忆 | `MessageChatMemoryAdvisor` + `CONVERSATION_ID` | Graph 侧:`threadId` + `MemorySaver`(示例 5) |
| 流式输出 | token 粒度(`ChatClient.stream()`) | 图节点粒度(`CompiledGraph.stream()`,示例 6) |
| 流程编排 | ❌ 无,业务代码 if/else | ✅ `StateGraph`(示例 3/4) |
| Agent 抽象 | ❌ 无(只有 ChatClient) | ✅ `ReactAgent` + 多 Agent/A2A(示例 5) |
| 厂商原生参数 | ❌(OpenAI 协议语义) | ✅ 思考模式等(示例 2) |

## 常见问题

### 概念问答(学习过程的提问整理)

**模型与平台**

**Q1:Qwen 和 DashScope 什么关系?阿里的模型都叫什么?**

答:Qwen(通义千问)是**模型**,DashScope(阿里云百炼)是**承载模型的平台**——鉴权用平台的 key,请求体里用模型名指定要哪个"商品",全行业都是这个模式。和智谱侧完全同构:

| | 模型(商品) | 平台(商店) | 本模块配置 |
|---|---|---|---|
| 阿里 | Qwen / qwen-plus | DashScope / 百炼 | `dashscope.api-key` + `dashscope.model` |
| 智谱 | GLM / glm-5.3-flash | open.bigmodel.cn | spring-ai-test 的 `glm.*` |

百炼(控制台产品名)和 DashScope(API 服务名)基本指同一个平台;货架上除 Qwen 外还托管 DeepSeek、Kimi 等三方模型。命名速记:qwen-turbo/plus/max 按档位(快/均衡/最强),qwen3 是新一代;Qwen-VL/Audio/Coder 是专精方向;QwQ 已并入 qwen3 的 `enableThinking` 开关(示例 2);通义万相(wanx/wan)是图像视频;text-embedding-v4/gte-rerank 是 RAG 基建。

**提示词与消息**

**Q2:`.system()` 和 `.systemPrompt()` 什么区别?**

答:都是给对话挂系统提示词的入口,最终都产出一条 SYSTEM 角色的消息,区别在**谁管这次调用**:

| 对比项 | `.system()` | `.systemPrompt()` |
|---|---|---|
| 是什么 | ChatClient 的方法 | SAA `agent.Builder` 的方法 |
| 所在层 | 单次请求构建期 | Agent 装配期,build 时设一次 |
| 生效范围 | 仅当次 `prompt()...call()`,下次要重新设 | Agent 内每次 LLM 调用(ReAct 循环每一跳)都自动带 |
| 模块用例 | 示例 4 各图节点 | 示例 5 ReactAgent |

为什么 ReactAgent 只能有一个 systemPrompt?Agent 内部 ReAct 循环每一跳都由框架帮你调 ChatClient,你插不进去,提示词必须 build 时一次设定;图节点模式每次调用自己管,所以三个应答节点能各挂各的 `.system()`。

**Agent 层**

**Q3:ReactAgent 和一次带工具的调用(ChatClient.tools())什么区别?**

答:差别不在"能不能调工具",而在**谁管循环和状态**。一次调用是"一锤子买卖":内部同样会执行"模型要工具→框架执行→回填→模型继续"的循环,但对你完全透明、结束即销毁;ReactAgent 是有生命周期的 Agent 对象:

| 对比维度 | 一次调用(`.tools()`) | ReactAgent |
|---|---|---|
| 本质 | 一次请求管线 | 框架生成的 ReAct 图(LLM 节点+工具节点的环) |
| 循环 | 有但透明,模型出答案即停 | 显式 ReAct 循环,轮数等参数可配 |
| 记忆 | 无,历史要自己拼 prompt | `saver`+`threadId` 自动带(示例 5) |
| 提示词/输出约束 | 每次调用手动设(`.system()`/`.entity()`) | 装配期设一次,终身生效 |
| 扩展协作 | Advisor(请求管线拦截器) | Hook/子 Agent/多 Agent 模式 |
| 适用 | 单步:分类/抽取/一次问答 | 多步:循环决策/跨轮记忆/多角色协作 |

最直观的分水岭是记忆:示例 5 两轮 `call` 同一个 threadId,第二轮不重复工单号 Agent 也记得;一次调用模式要自己拼历史。一句话:一次调用 = 临时工,ReactAgent = 有工位(threadId)、有岗位说明(systemPrompt)、有工作记录(saver)的员工。
