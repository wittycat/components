# spring-ai-test —— Spring AI 学习示例

[Spring AI](https://docs.spring.io/spring-ai/reference/) 是 Spring 官方的 AI 应用框架,本模块锁定 **1.1.8**(官方 BOM),配套父工程的 **Spring Boot 3.5.3**,要求 JDK 17+。包含 8 个**递进式** CLI 示例 + 1 个 **Spring Boot SSE 流式接口**,外加 PgVector 持久化篇(示例 10~12 + Web RAG 接口)、MCP 服务篇(Apifox 可连)和数据库驱动的动态 MCP 管理页,每个都是独立可运行的 main 类,注释里写清了每个知识点。

> **版本说明**:Spring AI 2.0.x 已面向 Spring Boot 4 发布,但父工程锁定 Boot 3.5,所以本模块用 1.1.x 稳定线;想学 2.x 时把父工程 parent 和 pom 里的 `spring-ai.version` 一起升级,API 思路不变。
>
> **学习主线**:从"一次对话"开始,依次掌握消息与提示词 → 流式输出 → 工具调用 → 会话记忆 → 结构化输出,最后用 RAG 让模型"基于你的资料回答"。核心 API 只有两个:`ChatClient`(门面,链式组装请求)和 `Advisor`(拦截器链,记忆/RAG 等横切能力即插即用)——前者对标 LangChain4j 的 `AiServices`,后者是 Spring AI 独有的 Filter 式设计。

## 环境准备(只做一次)

1. **JDK 17+**、Maven 3.9+
2. **配置智谱 API Key**(全部示例都需要):编辑 `src/main/resources/application-local.yml`(该文件已被 `.gitignore` 排除,不会提交),填入你的 key:

```yaml
glm:
  api-key: 你的key
  base-url: https://open.bigmodel.cn/api/coding/paas/v4
  model: glm-5.3-flash
  embedding-model: embedding-3
```

3. **PostgreSQL(示例 10~12 和全部 Web 接口需要)**:安装并启动 Postgres.app(端口 5432),然后建库并启用向量扩展:

```bash
createdb rag_demo                                  # 没建过才需要
psql -d rag_demo -c "CREATE EXTENSION IF NOT EXISTS vector;"
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
| 9 | `SpringAiApplication` | Spring Boot 集成:全模块唯一启动类,SSE 流式聊天(打字机)+ RAG 三接口同应用暴露 | `mvn -q spring-boot:run`(端口 8084) |

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

## PgVector 持久化篇(示例 10~12 + Web RAG)

示例 7/8 的向量库是内存版(`SimpleVectorStore`,重启即失)。这一篇换成 PostgreSQL + pgvector 扩展,把向量真正落库——`VectorStore` 接口不变,只换 starter(`spring-ai-starter-vector-store-pgvector`)和配置,这就是"换向量库不改业务代码"的现场演示。前置条件见上文环境准备第 3 条。

| # | 类 | 学什么 | 运行命令 |
|---|---|---|---|
| 10 | `pgvector.Example10_Embedding` | 裸 embedding + 手写余弦相似度:语义相关 0.87 vs 无关 0.32 | `mvn -q compile exec:java -Dexec.mainClass=com.wittycat.pgvector.Example10_Embedding` |
| 11 | `pgvector.Example11_Ingest` | 切块灌库:写入 PG 的 `vector_store` 表 | 同上,换类名 |
| 12 | `pgvector.Example12_Search` | HNSW 索引相似度检索(RAG 前半段) | 同上,换类名 |

CLI 示例会以 web(NONE) 启动一个临时 Spring 上下文,复用 application.yml 的全部配置(数据源、embedding 维度、pgvector 建表)。

### Web 版 RAG(和示例 9 同一个启动类)

RAG 三接口和示例 9 的 SSE 挂在同一个 `SpringAiApplication` 上(启动类放在 `com.wittycat` 包根,默认组件扫描就覆盖 `springai/` 和 `pgvector/` 两个平级业务包),一条命令启动:

```bash
mvn -q spring-boot:run
curl -X POST http://127.0.0.1:8084/rag/ingest     # 重新灌库,返回 {"chunks":4}
curl "http://127.0.0.1:8084/rag/search?q=HNSW%20索引"   # 只检索,看分数
curl "http://127.0.0.1:8084/rag/chat?q=切块太大或太小有什么问题"   # 完整 RAG
```

中文查询参数用 `curl -G --data-urlencode "q=中文问题"` 更省事。

### 易踩的坑(细节见 application.yml 注释)

- **维度不匹配**:GLM embedding-3 默认 2048 维,pgvector 的 HNSW 索引最多 2000 维,所以压到 1024(`spring.ai.openai.embedding.options.dimensions` 和 `spring.ai.vectorstore.pgvector.dimensions` 两处都要);改维度后先 `DROP TABLE vector_store` 再重灌,且不同维度的向量不能互相比较。
- **embeddings 404**:chat 走 coding 专用端点,embedding 单独走通用端点 `https://open.bigmodel.cn/api/paas/v4`(见 application.yml 的 `embedding.base-url`)。
- **连接失败**:启动类启动即连 PG,检查 Postgres.app 是否在运行(菜单栏大象图标)。若只想跑 SSE 不连库,可在启动类 `exclude` 掉 DataSource/PGVector 两项自动配置(条件装配,可精确关掉)。

## MCP 服务篇(Apifox 可连)

MCP(Model Context Protocol)是"AI 应用接外部工具"的开放协议,三个角色:**MCP Host**(装着 AI 的应用,如 Apifox/Claude/Cursor)→ **MCP Client**(Host 里的协议连接器)→ **MCP Server**(本应用)。本篇把模块挂成 MCP Server,工具写在 `mcp/McpTestTools`(普通方法 + `@Tool` 注解),由 `mcp/McpServerConfig` 的 `ToolCallbackProvider` 注册——和示例 4 是**同一个 `@Tool` 注解、两种出口**:喂给 ChatClient 是模型自用,注册进 MCP Server 是给外部 AI 应用跨进程调用。

| 工具 | 参数 | 作用 |
|---|---|---|
| `echo` | `message` | 回显消息,最小连通性验证 |
| `getCurrentTime` | `timezone`(可选,如 `Asia/Shanghai`/`UTC`) | 当前时间,默认系统时区 |
| `add` | `a`、`b`(数值) | 两数相加 |
| `ragSearch` | `query` | 在 pgvector 知识库里语义检索 top3(需 PG 已启动且已跑示例 11 灌库) |

### Apifox 连接步骤(需 Apifox ≥ 2.8.3)

1. 启动本模块:`mvn -q spring-boot:run`(和 SSE/RAG 同一个应用,8084);
2. Apifox 里新建接口,**协议类型选 MCP**,服务端地址粘贴 `http://127.0.0.1:8084/mcp`(粘贴 URL 会自动识别为 Streamable HTTP);
3. 点"连接"——成功后目录树列出 Tools(上表 4 个);
4. 选中工具,表单(或 JSON 编辑器)填参数 → "运行",响应区可看结果;Messages 标签页/信封模式还能看到完整 JSON-RPC 报文,正好对着 MCP 协议学。

> 为什么用 Streamable HTTP 而不是默认 SSE:Apifox 的 MCP 调试只支持 STDIO 和 Streamable HTTP 两种传输,所以 application.yml 里显式配了 `spring.ai.mcp.server.protocol: STREAMABLE`(端点默认 `/mcp`)。
>
> 安全提示:HTTP 传输默认**无鉴权**,任何能访问 8084 的客户端都能列出并调用全部工具——只在本机调试用,暴露到 localhost 之外前要自加安全边界(如 Spring Security)。

## 动态 MCP 篇（数据库驱动、界面管理）

动态 MCP 工具定义保存在 PostgreSQL 的 `springai_mcp_tool_registration` 表中，管理页位于
`http://127.0.0.1:8084/dynamic-mcp.html`。页面通过 REST API 修改数据库；应用随后把已启用定义同步到同一个
`/mcp` 的 `McpSyncServer`，并发送 `notifications/tools/list_changed`。因此 MCP 客户端不需要重启服务，
在支持通知的客户端中重新执行 `tools/list` 即可看到变化。

### 包结构（DDD 四层）

```
com.wittycat.dynamicmcp
├── interfaces/rest      # Controller + ExceptionHandler：HTTP 路由、DTO、错误码→状态码映射
├── application          # DynamicMcpToolService：用例编排（校验→落库→按状态同步运行时）
├── domain               # 模型/校验规则/异常 + 仓储、注册表两个接口（不碰 JDBC/MCP SDK）
└── infrastructure       # 接口落地：JdbcDynamicMcpToolRepository / DynamicMcpRegistrar / DynamicToolExecutor
```

依赖方向 `interfaces → application → domain ← infrastructure`：领域层只定义"要什么"
（`DynamicMcpToolRepository`、`DynamicMcpToolRegistry` 两个接口），基础设施层决定"怎么拿、
怎么同步"；ErrorCode 只含业务语义，HTTP 状态码集中在接口层 ExceptionHandler 决定；
数据库唯一键冲突由仓储实现翻译成领域异常。MCP SDK 类型被隔离在 `infrastructure/mcp`
一个包里，换掉 SDK 只动那一处。

管理接口如下：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dynamic-mcp/tools` | 查询未删除工具 |
| POST | `/dynamic-mcp/tools` | 新建工具，服务端强制为 `DISABLED` |
| PUT | `/dynamic-mcp/tools/{id}` | 修改定义，状态单独维护 |
| PUT | `/dynamic-mcp/tools/{id}/status` | 启用或停用，body 为 `{"status":"ENABLED"}` |
| DELETE | `/dynamic-mcp/tools/{id}` | 逻辑删除并从 MCP 移除 |
| POST | `/dynamic-mcp/tools/resync` | 清理动态运行时副本并按数据库重建 |

当前支持两类工具：`ECHO` 只回显参数，用来验证注册链路；`HTTP` 只允许访问
`dynamic-mcp.allowed-hosts` 配置中的主机，默认是 `127.0.0.1`、`localhost` 和 `::1`。HTTP 工具支持路径占位符
`{param}`、GET/DELETE query 参数和 POST/PUT/PATCH JSON body；请求和响应分别限制为 256 KiB 和 1 MiB，
连接、请求超时均为 10 秒，禁止自动重定向、用户信息和敏感 Header 透传。

启动时 SQL 初始化脚本会幂等创建表并插入 `echo_args`、`rag_kb_search` 两条启用工具。数据库是事实源，
运行时注册表是它的副本；页面操作先成功写库，再更新 MCP。工具名必须匹配 `^[A-Za-z0-9_-]{1,64}$`，
不能与 4 个静态工具重名。输入 Schema 只支持根 `object` 以及基础类型属性，暂不支持 `$ref`、`oneOf`、`anyOf`、`allOf`。

验证动态通知时，先用同一 MCP session 执行 `tools/list`，在管理页启用一个工具，观察
`notifications/tools/list_changed`，然后在同一 session 再执行 `tools/list`；停用和删除也应收到通知并看见工具消失。
再建立一个新 session 检查重连后的最终列表。客户端如果不消费通知，需要手动刷新或重连，但服务端列表已经实时更新。

这是学习模块，不提供登录鉴权、租户隔离或跨主机代理能力。不要把管理端口暴露到不受信任网络；生产场景应增加认证、
CSRF 防护、审计和更严格的 URL allowlist。

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
- **RAG**:入库 = `TokenTextSplitter` 切分 + `VectorStore.add()`;检索 = `similaritySearch` 按相似度取 Top-N(`similarityThreshold` 过滤无关内容治幻觉)。`SimpleVectorStore` 是内存实现;示例 10~12 已演示换成 PGVector(只换 starter + 配置,接口不变),生产亦可换 Milvus。

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

## 概念问答(学习过程的提问整理)

**切分与向量化**

**Q1:这套切分参数最大适合多大文本?(`TokenTextSplitter(150, 40, 5, 10000, true, List.of('\n'))`)**

答:理论上限由 maxNumChunks 决定:150 token/块 × 10000 块 ≈ 150 万 token(数百万字)。但真正瓶颈不在切分,而在入库成本(每块一次 embedding HTTP 调用)和检索方式(SimpleVectorStore 是全库余弦)。学习/演示几千字最舒服;生产上到几十万字就该换 PGVector/Milvus。

**Q2:embedding-3 有维度选项吗?**

答:默认 2048 维,基于 Matryoshka 技术支持 256/512/1024/2048 四档,降维可换检索速度、损一点精度。关键坑:换维度必须全库重新 embedding,不同维度的向量之间不能比较。

**Q3:一段文字向量化之后是什么?**

答:一个浮点数组(embedding-3 是 2048 个 float),本质是"语义坐标"——意思相近的文本,向量夹角小。不可逆:从向量还原不出原文,所以向量库通常把原文和向量存在一起,检索时按 id 取回原文。

**向量库选型与存储**

**Q4:生产常用哪个向量库?**

答:Java/Spring 生态首选 PGVector(团队已有 PostgreSQL 时零新增组件);中大规模、亿级向量用 Milvus;已有 ES 团队可用 ES 8+ 的 kNN。Spring AI 的 `VectorStore` 接口屏蔽差异,从 Simple 换成 PGVector/Milvus,业务代码不动。

**Q5:向量数据库的存储结构是怎样的?**

答:分三层:①向量数据区,每条记录 = id + 浮点数组 + 元数据;②ANN 索引(HNSW/IVF-PQ 等),近似最近邻检索,牺牲少量召回换数量级加速;③标量索引,支持按元数据过滤(如"只在某年份的文档里搜")。`SimpleVectorStore` 没有索引层,检索就是内存全量余弦(FLAT 模式),数据一大就慢。

**Q6:ANN 索引是向量数据库专有的吗?**

答:ANN(近似最近邻)是一类算法的统称,HNSW、IVF、DiskANN 都是具体实现,解决"高维空间找最近邻"问题——传统 B+ 树按值排序组织,做不了语义距离检索。向量数据库的核心价值就是把 ANN 索引产品化(分布式、增量更新、标量过滤)。

**Q7:MySQL 支持向量检索吗?**

答:社区版基本不支持:MySQL 9 有了 VECTOR 列类型,但没有距离函数和 ANN 索引(那是 HeatWave 商业云服务的能力),只能全表扫描后自己算余弦;MariaDB 11.8 社区版倒是完整支持。仓库里 ai-knowledge-base-test 就是"MySQL 存向量 + Java 算余弦"的小数据量实现,几万条以内可用。

**Q8:Milvus / PostgreSQL / MySQL 装完各占多少内存?**

答:

| 数据库 | 空载内存 | 备注 |
|---|---|---|
| Milvus Standalone | 约 1~2GB | 官方建议 8GB 内存起步 |
| PostgreSQL | 约 100MB | 加 PGVector 扩展也不重 |
| MySQL | 约 400MB | 社区版无向量检索能力,见 Q7 |

学习机不想扛 Milvus 的话,PGVector 是性价比最高的选择。

**Embedding 与调用成本**

**Q9:文字转向量需要调用 LLM 吗?**

答:不需要。走独立的 Embedding 模型(embedding-3),和对话模型是两种模型:输入一段文本、输出浮点数组,没有生成过程,价格便宜 1~2 个数量级。RAG 全链路的 embedding 调用 = 入库时 N 次(每块一次)+ 每次提问 1 次(问题转向量);贵的 chat 调用只有最后生成回答那 1 次。

**Q10:`vectorStore.add(chunks)` 内部调 Embedding 模型了吗?调了几次?**

答:调了。`SimpleVectorStore.doAdd()` 对每个 chunk 逐条 `embeddingModel.embed()`,N 个块就是 N 次 HTTP 调用——这也是示例 7 入库阶段慢的原因。

**Q11:`similaritySearch` 里问题也被转向量了吗?**

答:是。流程 = 先把 query embed 成向量 → 与库内所有向量算余弦 → 低于 similarityThreshold 的丢弃 → 按分数降序取 topK。所以每次检索固定多一次 embedding 调用。

**检索原理与调用协议**

**Q12:ES 查询和向量检索,原理有什么区别?**

答:

| | ES(BM25) | 向量检索 |
|---|---|---|
| 索引 | 倒排索引("词 → 文档列表") | ANN 索引(HNSW/IVF 等) |
| 匹配方式 | 字面统计:词频、IDF、长度归一化打分 | 语义坐标距离(余弦夹角) |
| 搜"电脑死机"能命中"计算机卡住"吗 | 不能,字面不同 | 能,语义相近 |
| 搜"ERROR-1042"精确错误码 | 更准 | 向量反而可能失手 |

两边的失效场景正好互补,生产常用混合检索:两路各召回一批,RRF 融合排序。

**Q13:调用 LLM 目前有几种协议?怎么区分?**

答:厂商原生主流 3 种:

| 协议 | 请求路径 | system 位置 | 流式收尾 | 谁在用 |
|---|---|---|---|---|
| OpenAI Chat Completions | `/chat/completions` | messages 数组里 | `data:[DONE]` | OpenAI 本家 + GLM/DeepSeek/Qwen/Kimi/vLLM/Ollama(**事实标准**) |
| Anthropic Messages | `/messages` | 请求体顶层 | `event:message_stop` | Claude;2025 年起国产厂商为接 Claude Code 纷纷加兼容端点 |
| Gemini generateContent | `:generateContent` | systemInstruction 字段 | data 流(`?alt=sse`) | Gemini 官方 |

拿到端点 30 秒区分法:看路径 → 看 system 位置 → 看流式收尾。MCP/A2A 不在这一层——分别管"Agent 接工具"和"Agent 间协作",底层照样落到模型调用协议。本模块就是"OpenAI 协议接 GLM":`spring-ai-starter-model-openai` + 改 base-url,换厂商零代码改动;仓库里 spring-ai-alibaba-test 是另一种接法(DashScope 原生协议 + 专属 starter)。

**消息、Advisor 与构件**

**Q14:MessageType 的 USER / ASSISTANT / SYSTEM / TOOL 是什么?**

答:消息角色枚举,标注"这条消息是谁说的",枚举值的小写字符串就是 OpenAI 协议报文 `messages[].role` 的字面值。

| 角色 | 谁在说 | 典型内容 | 在本模块里 |
|---|---|---|---|
| SYSTEM | 开发者 | 人设、规则、约束,优先级最高 | `.defaultSystem()` 设的系统提示词 |
| USER | 用户 | 提问 | 每次 `.user(...)` |
| ASSISTANT | 模型 | 模型以前说过的回答 | 记忆重发的历史、SSE 流式吐出的 token |
| TOOL | 你的 Java 代码 | 工具执行的**结果** | 示例 4 工具循环里框架自动喂回,不用手写 |

易混点:模型的"工具调用请求"不是 TOOL 消息,而是带 `tool_calls` 的 ASSISTANT 消息(content 为空);TOOL 消息必须带 `tool_call_id` 回指响应哪次调用。

**Q15:Advisor 有哪些常用成品?**

答:常用 6 个:

| 类 | 作用 | 备注 |
|---|---|---|
| `MessageChatMemoryAdvisor` | 会话记忆 | 示例 5 在用;窗口大小 + CONVERSATION_ID |
| `QuestionAnswerAdvisor` | RAG:检索 + 拼上下文 | 示例 8 在用;topK/相似度阈值 |
| `RetrievalAugmentationAdvisor` | 生产级 RAG 管线 | 查询改写/多路扩展/空结果兜底;**需手动加 `spring-ai-rag` 依赖** |
| `VectorStoreChatMemoryAdvisor` | 记忆存进向量库 | 长会话按语义捞历史 |
| `SimpleLoggerAdvisor` | 打印每轮请求/响应 | 调试神器,加一行就能看到完整报文 |
| `SafeGuardAdvisor` | 敏感词拦截 | 命中直接返回固定话术,不调模型 |

`ToolCallAdvisor`、`ChatModelCallAdvisor`/`ChatModelStreamAdvisor` 等是框架内部用的,别手动挂。自定义:实现 `BaseAdvisor` 的 `before()`/`after()` 两个方法(环绕通知,同步/流式一套代码通吃),审计、限流、脱敏都该写成 Advisor 而不是包一层 Service。组合时注意顺序语义:要让记忆存到用户原始问题,别让 RAG 注入后的长文本污染会话历史(见示例 8 注释第 2 条)。

**Q16:spring-ai-vector-store 和 spring-ai-advisors-vector-store 什么区别?**

答:上下两层,单向依赖(后者的 pom 明确依赖前者):

| | spring-ai-vector-store | spring-ai-advisors-vector-store |
|---|---|---|
| 定位 | 向量库**抽象层** | 建在抽象之上的 **ChatClient Advisor** |
| 类数量 | 91 个 | 3 个 |
| 核心内容 | `VectorStore` 接口、`SimpleVectorStore`、`SearchRequest`、元数据过滤 | `QuestionAnswerAdvisor`、`VectorStoreChatMemoryAdvisor` |
| 依赖 ChatClient | 否 | 是(经 spring-ai-client-chat) |
| 谁在用 | 示例 7(建库/入库/检索) | 示例 8(RAG Advisor 化) |

生产换向量库时加对应 starter 换掉 SimpleVectorStore,Advisor 层一行不动——示例 10~12 就是现场演示(本模块 pom 里的 `spring-ai-starter-vector-store-pgvector`)。当初 import `QuestionAnswerAdvisor` 编译报错,就是因为模型 starter 两个构件都不带,需在 pom 手动加。

**MCP 协议与进程模型**

**Q17:MCP 底层的通信框架是 Servlet 吗?**

答:不是。MCP 是应用层协议,不绑定任何通信框架——"协议/传输/载体"三层各管一件事:

| 层 | 管什么 | 可选项 |
|---|---|---|
| 协议 | 说什么话:JSON-RPC 2.0 报文 + MCP 自己的会话约定(initialize 握手、capabilities 协商、`Mcp-Session-Id`、listChanged 通知) | 固定 |
| 传输 | 走什么信道 | STDIO(进程管道,无 HTTP) / Streamable HTTP(POST /mcp,下行可选 SSE) |
| 载体 | 用什么框架收发 | Servlet、Spring MVC、WebFlux/Netty… |

一句话:**MCP 定协议,传输选信道,载体选框架,三者正交可替换。**本模块的 /mcp 走 Streamable HTTP 传输,由 `WebMvcStreamableServerTransportProvider` 挂进 Spring MVC,最终落在 Tomcat 的 Servlet 容器上——"底下是 Servlet"这个观察成立,但那是 starter 选了 MVC 栈的结果,不是 MCP 的属性(换成 webflux 版 starter 就跑 Netty,协议与业务代码零改动)。本地 SDK 0.18.3 里同一传输就有三套载体类:`StdioServerTransportProvider`(mcp-core)、`HttpServletStreamableServerTransportProvider`(mcp-core,原生 Servlet)、`WebMvcStreamableServerTransportProvider`(mcp-spring-webmvc,本模块在用)。

**Q18:STDIO 是什么?**

答:standard input/output,操作系统给每个进程自带的两条数据管道。作为 MCP 传输时:Host(Claude Desktop/Cursor 等)把 MCP Server 当**子进程**拉起,请求写进它的 stdin,响应从它的 stdout 读出,一行一条 JSON-RPC 消息——全程没有网络、没有端口、没有 HTTP。配置形态就是 command + args(Claude Desktop 配置里的 `{"command":"npx","args":[...]}`)。

| 维度 | STDIO | Streamable HTTP(本模块) |
|---|---|---|
| 信道 | OS 管道 stdin/stdout | HTTP POST /mcp |
| 部署 | Host 拉子进程,必须同机 | 独立服务,本机/远程都行 |
| 生命周期 | Host 管(Host 退它也退) | 应用自己管,多客户端共享 |
| 客户端配置 | command + args | URL(http://127.0.0.1:8084/mcp) |
| 典型用户 | Claude Desktop / Cursor 装本地工具 | Apifox / 跨进程 AI 应用(本模块) |

经典坑:STDIO 模式下 server 的日志**绝不能打 stdout**——stdout 是协议信道,混进一行日志客户端就解析失败,日志必须走 stderr 或文件。

**Q19:MCP Server 有它自己的进程吗?**

答:看传输。**"MCP Server"是协议角色,不是进程单位**——规范只规定消息怎么传,不管跑在哪个进程里。STDIO 模式必然是独立进程(Host 的子进程,装 N 个工具 = Host 身边 N 个子进程);HTTP 模式部署自由,可以是专属服务,也可以寄生在业务应用里。本模块是后者("合宿"形态):MCP Server 只是 SpringAiApplication 这一个 JVM 里的一个 Bean + /mcp 路由,和 /chat/stream、/rag/*、动态工具管理页同进程同端口。本机实测进程树:

```
launchd
└─ IntelliJ IDEA(或 mvn)
   └─ java(SpringAiApplication)   ← 应用唯一 JVM
      └─ 子进程:0 个
         (Tomcat、MCP Server、Hikari、动态工具注册表全是它内部的 Bean + 线程)
Apifox(独立进程) ──HTTP──▶ 8084 ──▶ 这个 JVM
```

动态 MCP 篇正是利用了合宿特点:工具注册表与业务同 JVM,`addTool/removeTool` 直接操作同一个 McpSyncServer,毫秒级生效、无需跨进程通知。

**Q20:spring-ai-test 跑起来有几个进程?**

答:应用本身永远 1 个 JVM、0 个子进程;总数取决于启动方式:

| 启动方式 | JVM 进程数 | 父子关系 |
|---|---|---|
| IDEA Run / `java -jar` | 1 | 挂在 IDEA 下,无子进程 |
| `mvn spring-boot:run` | 2 | Boot 3.x 插件总是 fork:Maven 自己一个 JVM,应用是它的子进程 |
| `mvn exec:java`(CLI 示例 10~12 用) | 1 | exec:java 在 Maven 同一个 JVM 里跑 main,不 fork |

进程内是几十个线程(实测 68 个:Tomcat 工作线程、Hikari、MCP 等),线程不等于进程。什么时候才会冒出子进程:代码里调 ProcessBuilder/Runtime.exec,或配了 MCP Client 且走 STDIO(Client 会把每个 stdio server 拉成自己的子进程)——本模块两者都没有。

**Q21:本模块用 STDIO 模式吗?**

答:没有,两种角色都不沾。三处实锤:①pom 用 `spring-ai-starter-mcp-server-webmvc`(HTTP 版;STDIO 应是不带 webmvc 的 `spring-ai-starter-mcp-server`);②yml 配 `protocol: STREAMABLE` + `mcp-endpoint: /mcp`(STDIO 根本没有 endpoint 配置);③Apifox 用 URL 连接(STDIO 只有 command+args,由 Host 当子进程拉起,不会自己起服务)。另外本模块也没配 `spring.ai.mcp.client`,连 MCP 客户端角色都没有。想改 STDIO 版三步:starter 换无 web 版、`spring.main.web-application-type: none` 关 web、日志改打 stderr——不过对"Apifox 远程调 + 界面管理动态工具"的目标,留在 HTTP 是对的。
