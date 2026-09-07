# langgraph4j-test —— LangGraph4j 学习示例

[LangGraph4j](https://github.com/langgraph4j/langgraph4j) 是 LangGraph(Python 最主流的 Agent 编排框架)的 Java 移植版,要求 JDK 17+。本模块包含 10 个**递进式**示例,每个都是独立可运行的 main 类,注释里写清了每个知识点。

> **学习主线**:把 AI 应用的流程画成一张"有状态的有向图"——先掌握图的原语(状态/节点/边/条件边),再学会给图加记忆、人机协同、模块化,最后用这些原语搭出 LLM 流水线、工具 Agent 和多智能体。
>
> 与同仓库的 `agentscope-test` 对比着学很有意思:AgentScope 把 ReAct 循环封装成了框架(开箱即用),LangGraph4j 则把循环的每一步交给你用图原语亲手搭出来(完全可控)。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置智谱 API Key**(示例 1~7 不需要,示例 8~10 需要):编辑 `src/main/resources/application-local.yml`(该文件已被 `.gitignore` 排除,不会提交),填入你的 key:

```yaml
glm:
  api-key: 你的key
  base-url: https://open.bigmodel.cn/api/coding/paas/v4
  model: glm-5.3-flash
```

模型走智谱的 OpenAI 兼容协议,零新增注册;`GlmClient` 是手写的极简 HTTP 客户端,不用任何 SDK。

配置读取优先级(在 `GlmConfig` 里实现,LLM 示例共用同一条路径):

```
-Dglm.xxx 系统属性  >  GLM_API_KEY 等环境变量  >  application-local.yml  >  application.yml(默认值)
```

## 十个示例(按顺序学)

| # | 类 | 学什么 | 需要 Key |
|---|---|---|---|
| 1 | `Example1_QuickStart` | 最小图:状态/节点/边,invoke 与 stream 两种跑法 | 否 |
| 2 | `Example2_StateAndChannels` | Channel 合并策略(appender 追加 vs 覆盖)、并行分支 fan-out/fan-in | 否 |
| 3 | `Example3_ConditionalEdges` | 条件边路由 + 回环(不达标就重试的骨架) | 否 |
| 4 | `Example4_Checkpoint` | MemorySaver 会话记忆、threadId 隔离、状态快照历史 | 否 |
| 5 | `Example5_Streaming` | 流式三玩法:for / streamSnapshots / forEachAsync | 否 |
| 6 | `Example6_HumanInTheLoop` | 中断(interruptBefore)→ 人工审阅 → 恢复(resume) | 否 |
| 7 | `Example7_Subgraph` | 子图作为节点,分层编排与状态共享 | 否 |
| 8 | `Example8_LlmNode` | 把 GLM 调用封装成节点:大纲→写稿→润色流水线 | 是 |
| 9 | `Example9_ToolAgent` | 手写 ReAct 循环:tool_calls → 执行工具 → 回填 → 继续思考 | 是 |
| 10 | `Example10_MultiAgent` | 多智能体监督者模式:主管用条件边派活给研究员/写手 | 是 |

运行方式统一为(以示例 1 为例,IDE 里直接跑 main 也可以):

```bash
mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example1_QuickStart
```

### 示例 3 预期输出(重点看循环)

grader 判卷 → 不及格走 coach 补课 → 回到 grader 重考,直到及格:

```
[grader] 第 1 次考试,得分 40
[coach] 没及格,安排补课……
[grader] 第 2 次考试,得分 75
[pass] 恭喜通过!共考了 2 次
```

### 示例 6 预期输出(重点看"两次调用之间发生了什么")

第 1 次 invoke 在 review 前停住(`getState(config).next()` 就是中断点);`updateState` 注入人工意见后 `invoke(GraphInput.resume(), config)` 恢复;驳回会改稿并**再次中断**等下一轮审阅——审批流的标准形态。

### 示例 9 预期输出(重点看 ReAct 循环)

问"北京和上海现在的天气,哪个更适合户外跑步?",会看到:

```
[agent] 带着 1 条历史消息询问 GLM……
[agent] GLM 回复:要求调用工具 [queryWeather, queryWeather]   ← 一次并行要两个城市
[tools] 执行 queryWeather({"city":"北京"})
[tools] 执行 queryWeather({"city":"上海"})
[agent] 带着 4 条历史消息询问 GLM……                        ← 工具结果已回填,继续思考
...
==================== 最终回答 ====================
(对比表格 + 结论:北京更适合,湿度/降雨分析……)
```

"Agent 会用工具"不是魔法:LLM 只决定**调什么、参数是什么**,执行工具的是你的 Java 代码,结果以 `role=tool` 消息喂回去,循环到模型不再要求调工具为止。

## 核心概念速查

```
StateGraph(图纸)      new StateGraph<>(schema, MyState::new) → addNode/addEdge/...
     ↓ compile()
CompiledGraph(可执行)  invoke(拿最终状态) / stream(逐节点观察)
     ↓ 运行时
NodeOutput            节点名 + 该时刻的状态;stream() 每执行完一个节点吐一个
     ↓
StateSnapshot         快照模式(streamSnapshots)专属:多出 next()(下一个节点),
                      由 checkpoint 生成,compile 时要挂 CheckpointSaver
```

- **状态(State)**:继承 `AgentState`(本质是 Map),节点返回 `Map<String,Object>` 表示**增量更新**;合并策略由 schema 里的 `Channel` 决定:不声明=覆盖,`Channels.appender`=追加(消息/日志列表专用)。
- **条件边**:`addConditionalEdges(源节点, 路由函数, 路由表)`,路由函数读状态返回字符串,路由表把字符串映射到目标节点。分支和循环(以及示例 9/10 的 Agent 骨架)全靠它。
- **记忆**:大模型无状态,"记得"=checkpoint 里存着上一步状态,同一 `threadId` 下次调用自动接着跑(示例 4);换 `threadId` 即会话隔离。
- **HITL**:`CompileConfig.builder().checkpointSaver(saver).interruptBefore("节点")` 编译时声明中断点,暂停后用 `updateState` 注入人工输入、`GraphInput.resume()` 恢复(示例 6)。
- **多智能体**:没有专门的"多智能体 API",就是 条件边 + LLM 决策路由(示例 10)。
- **兜底**:带环的图务必设 `RunnableConfig.builder().recursionLimit(n)`,防止模型停不下来。

## 目录结构

```
langgraph4j-test/
├── pom.xml                        # langgraph4j-core 1.8.27(LTS)+ snakeyaml + jackson
├── README.md
└── src/main/resources/
    ├── application.yml            # 默认配置(可提交 git;真实 key 别放这里)
    └── application-local.yml      # 本地私密配置(gitignore,放真实 api-key)
└── src/main/java/com/wittycat/langgraph4j/
    ├── GlmConfig.java             # glm.* 配置加载(与 agentscope-test 同一约定)
    ├── GlmClient.java             # 极简 OpenAI 兼容客户端(chat + tools)
    ├── Example1_QuickStart.java   # ①最小图:状态/节点/边/两种运行方式
    ├── Example2_StateAndChannels.java # ②Channel 合并策略 + 并行分支
    ├── Example3_ConditionalEdges.java # ③条件边路由与循环
    ├── Example4_Checkpoint.java   # ④会话记忆与快照历史
    ├── Example5_Streaming.java    # ⑤流式消费三玩法
    ├── Example6_HumanInTheLoop.java   # ⑥中断/人工审阅/恢复
    ├── Example7_Subgraph.java     # ⑦子图组合
    ├── Example8_LlmNode.java      # ⑧LLM 节点:写作流水线
    ├── Example9_ToolAgent.java    # ⑨手写 ReAct 工具调用 Agent
    └── Example10_MultiAgent.java  # ⑩多智能体监督者模式
```

## 常见问题

- **报"未找到智谱 API Key"**:`application-local.yml` 里没填有效 key(还是占位 `demo`),见"环境准备"。示例 1~7 不受影响,可先学。
- **版本怎么选**:1.8.x 是官方 LTS 维护分支,本模块锁定 `1.8.27`;1.9 尚在 beta,API 有变动(学习期不建议追)。
- **streamSnapshots 输出的不是 StateSnapshot?**:快照由 checkpoint 生成,compile 时必须挂 `CheckpointSaver`;且 `__END__` 输出的始终是普通 NodeOutput,消费端请用 `instanceof` 判断(示例 5 有注释)。
- **想可视化看图**:`graph.getGraph(GraphRepresentation.Type.MERMAID, ...)` 可以直接吐 Mermaid 图定义,贴到支持 Mermaid 的 Markdown 里就能看拓扑;想要交互式 Studio 界面,进阶可引 `langgraph4j-studio-springboot`。
- **进阶方向**:`langgraph4j-langchain4j` 生态(预置的 ReACT Agent / AgentExecutor,和 LangChain4j 的模型/工具体系打通)、`langgraph4j-postgres`/`mysql` 持久化 saver、`StreamingOutput` 做 token 级流式、官方仓库 `how-tos/` 目录里的更多场景示例。
