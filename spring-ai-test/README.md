# spring-ai-test —— Spring AI 学习示例

[Spring AI](https://docs.spring.io/spring-ai/reference/) 是 Spring 官方的 AI 应用框架,本模块锁定 **1.1.8**(官方 BOM),配套父工程的 **Spring Boot 3.5.3**,要求 JDK 17+。包含 8 个**递进式** CLI 示例 + 1 个 **Spring Boot SSE 流式接口**,每个都是独立可运行的 main 类,注释里写清了每个知识点。

> **版本说明**:Spring AI 2.0.x 已面向 Spring Boot 4 发布,但父工程锁定 Boot 3.5,所以本模块用 1.1.x 稳定线;想学 2.x 时把父工程 parent 和 pom 里的 `spring-ai.version` 一起升级,API 思路不变。
>
> **学习主线**:从"一次对话"开始,依次掌握消息与提示词 → 流式输出 → 工具调用 → 会话记忆 → 结构化输出,最后用 RAG 让模型"基于你的资料回答"。核心 API 只有两个:`ChatClient`(门面,链式组装请求)和 `Advisor`(拦截器链,记忆/RAG 等横切能力即插即用)——前者对标 LangChain4j 的 `AiServices`,后者是 Spring AI 独有的 Filter 式设计。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置智谱 API Key**(示例 1~9 都需要):编辑 `src/main/resources/application-local.yml`(该文件已被 `.gitignore` 排除,不会提交),填入你的 key:

```yaml
glm:
  api-key: 你的key
  base-url: https://open.bigmodel.cn/api/coding/paas/v4
  model: glm-5.3-flash
  embedding-model: embedding-3
```

模型走智谱的 OpenAI 兼容协议:Spring AI 侧只依赖 `spring-ai-starter-model-openai`,把 base-url 指向 GLM 端点即可;换 DeepSeek/Kimi/本地 vLLM 只是改 `base-url` 和 `model` 两个配置。

配置有两条通路,共享同一份 yml:

- **CLI 示例**:`GlmConfig` 用 SnakeYAML 手动读取(没启动 Spring),优先级 `-Dglm.xxx > 环境变量 > application-local.yml > application.yml`;
- **Web 示例**:Spring 自动配置读 `spring.ai.openai.*`,application.yml 里用 `${glm.xxx}` 占位符映射过去。

## 八个示例 + 1 个 Web 接口(按顺序学)

| # | 类 | 学什么 | 运行命令 |
|---|---|---|---|
| 1 | `Example1_QuickStart` | 最小可用:ChatClient 一次对话 | `mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example1_QuickStart` |
| 2 | `Example2_MessagesAndPrompt` | 系统提示词、Prompt 模板、token 用量 | 同上,换类名 |
| 3 | `Example3_Streaming` | 流式输出:Flux 逐 token 推送 | 同上,换类名 |
| 4 | `Example4_ToolCalling` | @Tool 工具调用:框架自动执行工具循环 | 同上,换类名 |
| 5 | `Example5_ChatMemory` | 会话记忆:MessageWindowChatMemory + CONVERSATION_ID 多会话隔离 | 同上,换类名 |
| 6 | `Example6_StructuredOutput` | 结构化输出:entity() 直接拿 record/enum/List | 同上,换类名 |
| 7 | `Example7_RagBasics` | RAG 基础:切分→向量化入库→手动检索→拼上下文 | 同上,换类名 |
| 8 | `Example8_RagAdvisor` | RAG 接 Advisor:QuestionAnswerAdvisor + 记忆组合 | 同上,换类名 |
| 9 | `web.ChatApplication` | Spring Boot + SSE 流式聊天接口(打字机) | `mvn -q spring-boot:run`(端口 8084) |

### 示例 4 预期输出(重点看"谁在执行工具")

问"北京和上海现在的天气怎么样?两地气温相差多少度?",控制台会先出现**你的 Java 代码**打印的日志,然后才是模型基于工具结果的回答:

```
>> [工具被调用] getWeather(北京) —— 这行是你的 Java 代码执行的
>> [工具被调用] temperatureDiff(12.0, 18.0) —— 这行是你的 Java 代码执行的
模型回答: 北京晴 12 度,上海小雨 18 度,两地气温相差 6 度。
```

"Agent 会用工具"不是魔法:LLM 只决定**调什么工具、参数是什么**,执行工具的是你的方法,结果喂回模型继续组织回答——循环由框架自动完成。

### 示例 5 预期输出(重点看会话隔离)

```
[s1] 你好小明!你最喜欢的数字是 7。
[s2] 抱歉,你还没有告诉我你的名字。        ← 新会话,记忆互不串台
[s1] 你叫小明,你最喜欢的数字是 7,7 × 6 = 42   ← 回到 s1,记忆还在
```

### 示例 7 预期输出(重点看检索分数)

```
embedding-3 把文本变成 2048 维向量,前 5 维: [0.023195464, ...]...
知识库切成了 10 段
问题: Spring AI 的 Advisor 是什么机制?
检索到的资料(分数越接近 1 越相关):
  [0.5359] Advisor 是 Spring AI 的请求/响应拦截器机制,思路类似 Servlet Filter 和 Spring AOP...
  [0.5160] Spring AI 是 Spring 官方推出的 AI 应用框架...
  [0.4354] Spring AI 提供可观测性支持...
模型回答: Advisor 是请求/响应拦截器机制,可以在请求前改写 Prompt、响应后做后处理...
```

注意 embedding-3 的余弦分整体偏低(0.4~0.6 区间),`similarityThreshold` 要按模型调;排序倒是准的——最相关的 Advisor 段落排第一。

### 示例 9(SSE 流式接口)

```bash
mvn -q spring-boot:run
# 另开终端,-N 关闭 curl 缓冲才能看到逐帧推送;中文参数需 URL 编码
curl -N "localhost:8084/chat/stream?sessionId=s1&message=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8DRAG"
```

每帧一个 `data:` 消息(一个 token),最后以 `data:[DONE]` 收尾;同一个 `sessionId` 连续请求,助手记得上文(原理同示例 5)。

## 核心概念速查

```
ChatModel / StreamingChatModel / EmbeddingModel    模型抽象,starter 自动配置成 Bean
        ↓ 组装
ChatClient(门面,链式 API)
        ├── .system() / .user()          消息角色 + Prompt 模板({变量})
        ├── .tools(...)                  @Tool 工具调用循环
        ├── .call().entity(类型)          结构化输出(BeanOutputConverter)
        ├── .stream()                    Flux 流式输出
        └── .defaultAdvisors(...)        Advisor 拦截器链(横切能力即插即用):
              ├── MessageChatMemoryAdvisor   会话记忆(CONVERSATION_ID 选会话)
              └── QuestionAnswerAdvisor      RAG(先检索、再拼上下文)
```

- **Advisor 链**:Spring AI 最有辨识度的设计,思路同 Servlet Filter / AOP——请求依次穿过拦截器,每个拦截器可改写 Prompt(记忆注入历史、RAG 注入资料)或处理响应;日志、审计、敏感词过滤都该写成 Advisor。
- **自动配置**:`spring-ai-starter-model-openai` 读了 `spring.ai.openai.*` 后,容器里自动有 `ChatModel`/`EmbeddingModel`/`ChatClient.Builder` 三个 Bean——这是它相对 LangChain4j"手动 new"的最大差异。
- **记忆**:大模型无状态,"记得" = 框架把历史消息随每次请求重发;`MessageWindowChatMemory` 滑动窗口防爆内存,`CONVERSATION_ID` 隔离会话(内存实现重启即失,生产换持久化)。
- **RAG**:入库 = `TokenTextSplitter` 切分 + `VectorStore.add()`;检索 = `similaritySearch` 按相似度取 Top-N(`similarityThreshold` 过滤无关内容治幻觉)。`SimpleVectorStore` 是内存实现,生产换 PGVector/Milvus,接口不变。

## 和 langchain4j-test 对照着学(概念互通)

| 概念 | LangChain4j | Spring AI |
|---|---|---|
| 模型抽象 | `ChatModel` / `StreamingChatModel` / `EmbeddingModel` | 同名接口,职责一致 |
| 高层门面 | `AiServices`(动态代理接口) | `ChatClient`(链式 API) |
| 横切能力 | 组装时挂到 AiServices | **Advisor 拦截器链**(核心差异) |
| 会话记忆 | `MessageWindowChatMemory` + `@MemoryId` | 同名类 + `CONVERSATION_ID` 参数 |
| 工具调用 | `@Tool`(dev.langchain4j) | `@Tool`(org.springframework.ai) |
| 结构化输出 | 接口方法返回类型自动映射 | `.call().entity(类型)` |
| RAG 检索 | `ContentRetriever` / `RetrievalAugmentor` | `QuestionAnswerAdvisor` |
| 向量库 | `EmbeddingStore`(InMemory) | `VectorStore`(Simple) |
| Spring 集成 | 手动 new,框架无关 | starter 自动配置,长在 Spring 里 |

## 目录结构

```
spring-ai-test/
├── pom.xml                            # spring-ai-bom 1.1.8 + starter-model-openai + spring-boot-starter-web
├── README.md
└── src/main/
    ├── resources/
    │   ├── application.yml            # 默认配置(glm.* + spring.ai.openai.* 占位符映射)
    │   ├── application-local.yml      # 本地私密配置(gitignore,放真实 api-key)
    │   └── rag/
    │       └── knowledge-springai.txt # 知识库:Spring AI 框架知识(示例 7/8)
    └── java/com/wittycat/springai/
        ├── GlmConfig.java             # glm.* 配置加载(与 agentscope-test 同一约定)
        ├── GlmModels.java             # 模型工厂:手动构建 ChatModel/EmbeddingModel
        ├── RagDocs.java               # classpath 文档加载
        ├── Example1_QuickStart.java   # ①最小对话
        ├── Example2_MessagesAndPrompt.java  # ②系统提示词/模板/用量
        ├── Example3_Streaming.java    # ③流式输出
        ├── Example4_ToolCalling.java  # ④工具调用
        ├── Example5_ChatMemory.java   # ⑤会话记忆
        ├── Example6_StructuredOutput.java   # ⑥结构化输出
        ├── Example7_RagBasics.java    # ⑦RAG 三环节手动版
        ├── Example8_RagAdvisor.java   # ⑧RAG 接入 Advisor + 记忆组合
        └── web/
            ├── ChatApplication.java   # ⑨Spring Boot 启动类(端口 8084)
            └── ChatController.java    #   SSE 流式聊天接口(Flux ↔ ServerSentEvent)
```

## 常见问题

- **报"未找到智谱 API Key"**:`application-local.yml` 里没填有效 key(还是占位 `demo`),见"环境准备"。
- **CLI 示例为什么不启动 Spring**:聚焦 API 本身,启动快、干扰少;自动配置的魅力在示例 9 里看——注入 `ChatClient.Builder` 一行装配代码都没有。
- **curl 示例 9 返回 400**:中文参数必须 URL 编码(浏览器地址栏会自动做,curl 不会);或改用 `--data-urlencode` 的 `curl -G` 写法。
- **端口冲突**:Web 示例占 8084,和仓库其他模块错开;要改就动 application.yml 的 `server.port`。
- **embedding 报错**:向量模型走同一个端点,若换的厂商不提供 embedding 接口,给向量模型单独配 `base-url`。
- **想升级 2.x**:Spring AI 2.0.x 面向 Spring Boot 4,需把父工程 `spring-boot-starter-parent` 和本模块 `spring-ai.version` 一起升级。
