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

## 常见问题

### 运行排错

- **报"未找到智谱 API Key"**:`application-local.yml` 里没填有效 key(还是占位 `demo`),见"环境准备"。
- **CLI 示例为什么不启动 Spring**:聚焦 API 本身,启动快、干扰少;自动配置的魅力在示例 9 里看——注入 `ChatClient.Builder` 一行装配代码都没有。
- **curl 示例 9 返回 400**:中文参数必须 URL 编码(浏览器地址栏会自动做,curl 不会);或改用 `--data-urlencode` 的 `curl -G` 写法。
- **端口冲突**:Web 示例占 8084,和仓库其他模块错开;要改就动 application.yml 的 `server.port`。
- **embedding 报错**:向量模型走同一个端点,若换的厂商不提供 embedding 接口,给向量模型单独配 `base-url`。
- **想升级 2.x**:Spring AI 2.0.x 面向 Spring Boot 4,需把父工程 `spring-boot-starter-parent` 和本模块 `spring-ai.version` 一起升级。

### 概念问答(学习过程的提问整理)

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

答:Milvus Standalone 空载约 1~2GB(官方建议 8GB 内存起步);PostgreSQL 空载约 100MB;MySQL 约 400MB。学习机不想扛 Milvus 的话,PGVector 是性价比最高的选择。

**Embedding 与调用成本**

**Q9:文字转向量需要调用 LLM 吗?**

答:不需要。走独立的 Embedding 模型(embedding-3),和对话模型是两种模型:输入一段文本、输出浮点数组,没有生成过程,价格便宜 1~2 个数量级。RAG 全链路的 embedding 调用 = 入库时 N 次(每块一次)+ 每次提问 1 次(问题转向量);贵的 chat 调用只有最后生成回答那 1 次。

**Q10:`vectorStore.add(chunks)` 内部调 Embedding 模型了吗?调了几次?**

答:调了。`SimpleVectorStore.doAdd()` 对每个 chunk 逐条 `embeddingModel.embed()`,N 个块就是 N 次 HTTP 调用——这也是示例 7 入库阶段慢的原因。

**Q11:`similaritySearch` 里问题也被转向量了吗?**

答:是。流程 = 先把 query embed 成向量 → 与库内所有向量算余弦 → 低于 similarityThreshold 的丢弃 → 按分数降序取 topK。所以每次检索固定多一次 embedding 调用。

**检索原理与调用协议**

**Q12:ES 查询和向量检索,原理有什么区别?**

答:ES 的 BM25 走倒排索引("词 → 文档列表"),靠词频、IDF、长度归一化打分,是字面统计匹配;向量检索把文本映射成语义坐标,靠向量距离找"意思最近"的。失效场景互补:搜"电脑死机"找不到"计算机卡住"(字面不同、语义近,向量赢);搜"ERROR-1042"这种精确错误码,BM25 反而更准。生产常用混合检索:两路各召回一批,RRF 融合排序。

**Q13:调用 LLM 目前有几种协议?怎么区分?**

答:厂商原生主流 3 种:**OpenAI Chat Completions**(事实标准,GLM/DeepSeek/Qwen/Kimi/vLLM/Ollama 都提供兼容端点)、**Anthropic Messages**(Claude;2025 年起国产厂商为接 Claude Code 纷纷加兼容端点)、**Gemini generateContent**。30 秒区分法:看路径(`/chat/completions` vs `/messages` vs `:generateContent`)、看 system 位置(在 messages 数组里 vs 请求体顶层)、看流式收尾(`data:[DONE]` vs `event:message_stop`)。MCP/A2A 不在这一层——分别管"Agent 接工具"和"Agent 间协作",底层照样落到模型调用协议。本模块就是"OpenAI 协议接 GLM":`spring-ai-starter-model-openai` + 改 base-url,换厂商零代码改动;仓库里 spring-ai-alibaba-test 是另一种接法(DashScope 原生协议 + 专属 starter)。
