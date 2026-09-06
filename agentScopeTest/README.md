# agentScopeTest —— AgentScope Java 学习示例

[AgentScope](https://github.com/agentscope-ai/agentscope-java) 是阿里 ModelScope 团队开源的 Agent 框架(Java 版,要求 JDK 17+)。本模块包含 4 个**递进式**示例,每个都是独立可运行的 main 类,注释里写清了每个知识点。

> **不涉及前端**:学习核心全在后端(模型接入、ReAct 循环、工具调用、会话记忆、流式接口),用控制台和 curl 验证即可。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置智谱 API Key**(推荐方式):编辑 `src/main/resources/application-local.yml`(该文件已被 `.gitignore` 排除,不会提交),填入你的 key(就是 `ai-agent/backend` 里那个):

```yaml
glm:
  api-key: 你的key
  base-url: https://open.bigmodel.cn/api/coding/paas/v4
  model: glm-5.3-flash
```

模型走智谱的 OpenAI 兼容协议,零新增注册。

配置读取优先级(在 `ModelFactory` 里实现,4 个示例共用同一条路径):

```
-Dglm.xxx 系统属性  >  GLM_API_KEY 等环境变量  >  application-local.yml  >  application.yml(默认值)
```

> 提醒:`application-local.yml` 不会进 git,但会被打进 jar 包——不要把带 key 的 jar 发布出去。

## 五个示例(按顺序学)

| # | 类 | 学什么 | 运行命令 |
|---|---|---|---|
| 1 | `Example1_QuickStart` | 创建 Agent、单次调用、拿到回复 | `mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example1_QuickStart` |
| 2 | `Example2_ToolCall` | 自定义工具 + ReAct 循环事件流 | 把 mainClass 换成 `...Example2_ToolCall` |
| 3 | `Example3_Memory` | 多轮会话记忆、会话隔离、清空记忆 | 把 mainClass 换成 `...Example3_Memory` |
| 4 | `Example4_StreamingRest` | Spring Boot + SSE 流式聊天接口 | `mvn -q spring-boot:run` |
| 5 | `Example5_MultiAgent` | 多智能体:编排式流水线 + 委托式(Agent-as-Tool)协作 | 把 mainClass 换成 `...Example5_MultiAgent` |

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

## 核心概念速查

```
ChatModel(模型)        OpenAIChatModel + GLMFormatter → 智谱 GLM(OpenAI 兼容协议)
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
agentScopeTest/
├── pom.xml                        # agentscope-harness 2.0.2 + openai 扩展 + spring-boot-starter-web
└── src/main/resources/
    ├── application.yml            # 默认配置(可提交 git;真实 key 别放这里)
    └── application-local.yml      # 本地私密配置(gitignore,放真实 api-key)
└── src/main/java/com/wittycat/agentscope/
    ├── ModelFactory.java          # 模型工厂:GLM 接入 + yml 配置加载
    ├── Example1_QuickStart.java   # ①最小 Agent
    ├── Example2_ToolCall.java     # ②工具调用与 ReAct 循环
    ├── Example3_Memory.java       # ③多轮会话记忆
    ├── Example4_StreamingRest.java# ④Spring Boot 启动类(端口 8081)
    ├── Example5_MultiAgent.java   # ⑤多智能体:编排式 + 委托式协作
    └── web/AgentChatController.java # POST /api/agent/chat(SSE 流式)
```

## 常见问题

- **报"未找到智谱 API Key"**:`application-local.yml` 里没填有效 key(还是占位 `demo`),见"环境准备"。
- **想换模型**:改 `application-local.yml` 里的 `glm.model`/`glm.base-url` 即可。其他 OpenAI 兼容服务(DeepSeek/Kimi/vLLM 本地模型)同样只改这两项;通义千问引 `agentscope-extensions-model-dashscope` 用 `DashScopeChatModel`。
- **依赖说明**:`agentscope-harness` 是官方推荐入口(内置 workspace/记忆管理,传递引入 `agentscope-core` 的 `ReActAgent`);本示例只用到了它的核心 Agent 能力。
- **版本**:AgentScope 2.x 与 1.x API 不兼容,本模块锁定 `2.0.2`。官方文档:<https://java.agentscope.io>
