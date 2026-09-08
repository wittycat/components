# langchain4j-test —— LangChain4j 学习示例

[LangChain4j](https://docs.langchain4j.dev/) 是 Java 生态最主流的大模型应用框架(对标 Python 的 LangChain),本模块锁定 **1.20.0**(官方 BOM),要求 JDK 17+。包含 10 个**递进式** CLI 示例 + 1 个 **Spring Boot SSE 流式接口**,每个都是独立可运行的 main 类,注释里写清了每个知识点。

> **学习主线**:从"一次对话"开始,依次掌握消息与提示词 → 流式输出 → 工具调用 → 会话记忆 → 结构化输出,最后用 RAG(检索增强生成)让模型"基于你的资料回答"。核心 API 只有两个:`ChatModel`(发消息拿回复)和 `AiServices`(声明一个 Java 接口,框架用动态代理把模型/工具/记忆/RAG 全部组装进去)。
>
> 与同仓库两个模块对比着学很有意思:`agentscope-test` 把 ReAct 循环封装成 Agent 框架(开箱即用),`langgraph4j-test` 用图原语亲手搭循环(完全可控),而 LangChain4j 走的是**接口抽象 + 可组合中间件**路线——三家思路不同,工具调用/记忆/RAG 的概念完全互通,学完一个再学另外两个会非常快。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置智谱 API Key**(示例 1~11 都需要):编辑 `src/main/resources/application-local.yml`(该文件已被 `.gitignore` 排除,不会提交),填入你的 key:

```yaml
glm:
  api-key: 你的key
  base-url: https://open.bigmodel.cn/api/coding/paas/v4
  model: glm-5.3-flash
  embedding-model: embedding-3
```

模型走智谱的 OpenAI 兼容协议:LangChain4j 侧只依赖 `langchain4j-open-ai`,把 baseUrl 指向 GLM 端点即可,零新增注册;换 DeepSeek/Kimi/本地 vLLM 只是改 `base-url` 和 `model` 两个配置。

配置读取优先级(在 `GlmConfig` 里实现,与 agentscope-test / langgraph4j-test 同一约定):

```
-Dglm.xxx 系统属性  >  GLM_API_KEY 等环境变量  >  application-local.yml  >  application.yml(默认值)
```

## 十个示例 + 1 个 Web 接口(按顺序学)

| # | 类 | 学什么 | 运行命令 |
|---|---|---|---|
| 1 | `Example1_QuickStart` | 最小可用:创建 ChatModel、一次对话 | `mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example1_QuickStart` |
| 2 | `Example2_MessagesAndPrompt` | 消息角色、手动多轮、token 用量、PromptTemplate | 同上,换类名 |
| 3 | `Example3_Streaming` | 流式输出:StreamingChatModel 逐 token 回调 | 同上,换类名 |
| 4 | `Example4_ToolCall` | @Tool 工具调用:AiServices 自动执行工具循环 | 同上,换类名 |
| 5 | `Example5_Memory` | 会话记忆:MessageWindowChatMemory + @MemoryId 多会话隔离 | 同上,换类名 |
| 6 | `Example6_StructuredOutput` | 结构化输出:从评论抽取 record/enum/List | 同上,换类名 |
| 7 | `Example7_RagBasics` | RAG 基础:切分→向量化→入库→手动检索→拼上下文 | 同上,换类名 |
| 8 | `Example8_RagAiServices` | RAG 进 AI 服务:ContentRetriever 实现"和文档对话" | 同上,换类名 |
| 9 | `Example9_Multimodal` | 多模态:现场画一张柱状图让模型读图 | 同上,换类名 |
| 10 | `Example10_AdvancedRag` | RAG 高级:查询改写 + 知识库路由管线 | 同上,换类名 |
| 11 | `web.ChatApplication` | Spring Boot + SSE 流式聊天接口(打字机) | `mvn -q spring-boot:run`(端口 8083) |

### 示例 4 预期输出(重点看"谁在执行工具")

问"我买了 3 斤苹果,每斤 6 块 5",控制台会先出现**你的 Java 代码**打印的日志,然后才是模型基于工具结果的回答:

```
>> [工具被调用] calculate(3*6.5) —— 这行是本地 Java 代码执行的
答: 一共是 19.5 元。
```

"Agent 会用工具"不是魔法:LLM 只决定**调什么工具、参数是什么**,执行工具的是你的方法,结果喂回模型继续组织回答——循环由 AiServices 自动完成(对照 `langgraph4j-test` 示例 9 的手写版,一眼看懂框架帮你省了什么)。

### 示例 5 预期输出(重点看会话隔离)

```
[s1] 你叫小明,你最喜欢的数字是 7。
[s2] 抱歉,我不知道您的名字。        ← 新会话,记忆互不串台
[s1] 7 × 6 = 42                     ← 回到 s1,记忆还在
```

### 示例 7 预期输出(重点看检索分数)

```
文档切成了 10 段
问题: AiServices 是怎么工作的?
检索到的资料(分数越接近 1 越相关):
  [0.8303] 等兼容服务。 AiServices 是 LangChain4j 中最常用的 API。开发者只需要声明一个 Java 接...
  [0.7961] ...
模型回答: 1. 开发者声明 Java 接口... 2. JDK 动态代理生成实现... 3. 自动组装能力...
```

注意检索命中的片段可能带着上下段的重叠文字("等兼容服务。")——这是 `DocumentSplitters.recursive(200, 30)` 的 overlap 在起作用(避免句子被切断),也提示**切分粒度是 RAG 调优的第一杠杆**。

### 示例 10 预期输出(重点看指代消解与路由)

第二问只有"**它**的第一个阶段是为了解决什么问题?",但回答准确解释了查询转换阶段——因为 `CompressingQueryTransformer` 结合聊天历史把"它"改写成了完整问题再去检索;第三问被 `LanguageModelQueryRouter` 路由到了 AI 概念知识库。

### 示例 9 说明

图片是运行时用 Java2D 现场画的柱状图(Java 60 / Python 70 / Go 40 / Rust 25),不依赖任何图片文件。实测 `glm-5.3-flash` 支持图片输入,能准确读出柱子数量、各项数值并指出最高柱(Python 70)。

### 示例 11(SSE 流式接口)

```bash
mvn -q spring-boot:run
# 另开终端,-N 关闭 curl 缓冲才能看到逐帧推送;中文参数需 URL 编码
curl -N "localhost:8083/chat/stream?sessionId=s1&message=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8DRAG"
```

每帧一个 `data:` 消息(一个 token),最后以 `data:[DONE]` 收尾;同一个 `sessionId` 连续请求,助手记得上文(原理同示例 5)。

## 核心概念速查

```
ChatModel / StreamingChatModel          模型抽象(一次性 / 流式),厂商实现可替换
        ↓ 组装
AiServices(动态代理)                    声明一个接口,框架生成实现:
        ├── .chatModel()                基础对话
        ├── .tools(...)                 @Tool 工具调用循环
        ├── .chatMemory / Provider      会话记忆(@MemoryId 选会话)
        ├── .contentRetriever()         单知识库 RAG(简化形态)
        └── .retrievalAugmentor()       完整 RAG 管线(高级形态,二选一)
        ↓ RAG 管线(DefaultRetrievalAugmentor 四阶段,均可自定义)
Transform(改写查询) → Route(选知识库) → Retrieve(向量检索) → Fuse(合并结果)
```

- **模型抽象**:`ChatModel`/`StreamingChatModel`/`EmbeddingModel` 三个接口对应三类能力;OpenAI 协议是事实标准,`langchain4j-open-ai` 换 baseUrl 即接 GLM(见 `GlmModels`)。
- **记忆**:大模型无状态,"记得" = 框架把历史消息随每次请求重发;`MessageWindowChatMemory` 滑动窗口防爆内存,`ChatMemoryProvider` 按 memoryId 隔离会话(内存实现重启即失,生产换持久化 `ChatMemoryStore`)。
- **结构化输出**:接口方法返回 record/enum/List,框架负责"让模型按 JSON 输出 → 解析 → 失败重试",业务代码直接拿强类型对象。
- **RAG**:入库 = `DocumentSplitters` 切分 + `EmbeddingStoreIngestor` 向量化入库;检索 = `EmbeddingSearchRequest` 按相似度取 Top-N(可用 `minScore` 过滤无关内容治幻觉)。检索器的"简化/高级"两种挂法**不要同时挂**。
- **多模态**:一条 `UserMessage` 放多个 `Content`(`TextContent` + `ImageContent`),图片支持 base64 或 URL;模型需支持视觉。

## 目录结构

```
langchain4j-test/
├── pom.xml                            # langchain4j-bom 1.20.0 + open-ai + spring-boot-starter-web
├── README.md
└── src/main/
    ├── resources/
    │   ├── application.yml            # 默认配置(可提交 git;真实 key 别放这里)
    │   ├── application-local.yml      # 本地私密配置(gitignore,放真实 api-key)
    │   └── rag/
    │       ├── knowledge-java.txt     # 知识库一:Java/LangChain4j 框架知识(示例 7/8/10)
    │       └── knowledge-ai.txt       # 知识库二:AI 通用概念(示例 10 路由演示)
    └── java/com/wittycat/langchain4j/
        ├── GlmConfig.java             # glm.* 配置加载(与 agentscope-test 同一约定)
        ├── GlmModels.java             # 模型工厂:ChatModel/Streaming/Embedding 三合一
        ├── RagDocs.java               # classpath 文档加载(带来源元数据)
        ├── Example1_QuickStart.java   # ①最小对话
        ├── Example2_MessagesAndPrompt.java  # ②消息角色/多轮/模板
        ├── Example3_Streaming.java    # ③流式输出
        ├── Example4_ToolCall.java     # ④工具调用
        ├── Example5_Memory.java       # ⑤会话记忆
        ├── Example6_StructuredOutput.java   # ⑥结构化输出
        ├── Example7_RagBasics.java    # ⑦RAG 三环节手动版
        ├── Example8_RagAiServices.java# ⑧RAG 接入 AiServices
        ├── Example9_Multimodal.java   # ⑨多模态读图
        ├── Example10_AdvancedRag.java # ⑩查询改写 + 知识库路由
        └── web/
            ├── ChatApplication.java   # ⑪Spring Boot 启动类(端口 8083)
            └── ChatController.java    #   SSE 流式聊天接口(TokenStream ↔ SseEmitter)
```

## 常见问题

- **报"未找到智谱 API Key"**:`application-local.yml` 里没填有效 key(还是占位 `demo`),见"环境准备"。
- **curl 示例 11 返回 400**:中文参数必须 URL 编码(浏览器地址栏会自动做,curl 不会);或改用 `--data-urlencode` 的 `curl -G` 写法。
- **多模态报错(模型不支持图片)**:换支持视觉的模型再跑,例如 `-Dglm.model=glm-4v-flash`(只用改这一个系统属性,代码零改动)。
- **embedding 报错**:向量模型走同一个端点,若你换的厂商不提供 embedding 接口,给向量模型单独配 `base-url`(GlmConfig 的配置项对 embedding 同样生效,可加 `glm.embedding-base-url` 扩展)。
- **示例 8 问无关问题还是给了长回答**:模型收到空上下文时,有的模型会说"没有这个内容",有的会复述系统提示词后展开——把 `minScore` 调高(如 0.6)或强化系统提示词里"没有就说没有"的约束即可。
- **版本怎么选**:本模块锁定 1.20.0(2026-09 发布的稳定版);LangChain4j 迭代很快,升级只需改 pom 里的 `langchain4j.version` 一个属性(BOM 统一管理所有子模块)。
