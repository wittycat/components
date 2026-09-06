# AI Agent 智能体应用

基于 Spring Boot + Vue 3 的大模型智能体应用，集成智谱 GLM 大模型，支持 RAG 检索增强生成、Tool Calling 工具调用和对话记忆。

## 功能特性

- **流式对话 (SSE)**：实时显示大模型生成过程
- **RAG 知识库**：上传文档，智能体检索相关知识回答
- **Tool Calling**：支持 Shell 命令执行、文件读写
- **对话记忆**：自动保存上下文，支持多轮对话
- **会话管理**：创建、切换、删除对话

## 项目结构

```
ai-agent/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/agent/
│       │   ├── AiAgentApplication.java       # 启动类
│       │   ├── config/                         # 配置（GLM、CORS、Agent）
│       │   ├── controller/                     # REST API
│       │   │   ├── ChatController.java         # 对话 + SSE 流式
│       │   │   └── KnowledgeController.java    # 知识库管理
│       │   ├── dto/                            # 数据传输对象
│       │   ├── entity/                         # JPA 实体
│       │   ├── repository/                     # 数据访问层
│       │   ├── service/
│       │   │   ├── GlmClient.java              # GLM API 客户端
│       │   │   ├── AgentService.java           # Agent 核心逻辑
│       │   │   └── ConversationService.java    # 对话记忆
│       │   ├── rag/                            # RAG 模块
│       │   │   ├── TextChunker.java            # 文本分块
│       │   │   ├── DocumentIngestionService.java
│       │   │   └── RagService.java             # 检索服务
│       │   └── tool/                           # 工具调用模块
│       │       ├── AgentTool.java              # 工具接口
│       │       ├── ToolRegistry.java           # 工具注册
│       │       ├── ToolExecutor.java           # 工具执行
│       │       ├── ShellTool.java              # Shell 命令
│       │       ├── FileReadTool.java           # 读文件
│       │       └── FileWriteTool.java          # 写文件
│       └── resources/
│           ├── application.yml
│           └── schema.sql
└── frontend/                         # Vue 3 前端
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.vue                   # 主布局（侧边栏 + 聊天）
        ├── components/
        │   └── ChatWindow.vue        # 聊天窗口
        └── api/
            └── chat.js               # API + SSE 客户端
```

## 环境要求

- Java 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

## 快速开始

### 1. 准备 MySQL 数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ai_agent DEFAULT CHARACTER SET utf8mb4;"
```

修改 `backend/src/main/resources/application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_agent?...
    username: root
    password: your-password
```

### 2. 配置 GLM API Key

在 [智谱开放平台](https://open.bigmodel.cn/) 获取 API Key，然后：

```bash
export ZHIPU_API_KEY=your-zhipu-api-key
```

或直接修改 `application.yml` 中的 `glm.api-key`。

### 3. 启动后端

```bash
cd ai-agent/backend
mvn spring-boot:run
```

后端启动在 http://localhost:8080

### 4. 启动前端

```bash
cd ai-agent/frontend
npm install
npm run dev
```

前端启动在 http://localhost:5173

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/stream` | SSE 流式对话 |
| GET | `/api/conversations` | 获取对话列表 |
| POST | `/api/conversations` | 创建新对话 |
| GET | `/api/conversations/{id}/messages` | 获取消息历史 |
| DELETE | `/api/conversations/{id}` | 删除对话 |
| POST | `/api/knowledge/upload` | 上传知识库文档 |
| GET | `/api/knowledge/documents` | 列出知识库文档 |

## 架构说明

### RAG 流程

1. 用户上传文档 → 文本分块 → 生成 Embedding 向量 → 存入 MySQL
2. 用户提问 → 向量/关键词检索 Top-K 相关块 → 注入 System Prompt → LLM 生成回答

### Tool Calling 流程

1. LLM 返回 `tool_calls` → Agent 解析工具名和参数
2. `ToolExecutor` 执行对应工具（Shell / 文件读写）
3. 工具结果作为 `tool` 角色消息回传 LLM → 生成最终回答

### 对话记忆

- 每次对话自动保存 user/assistant/tool 消息到 MySQL
- 加载最近 20 条消息作为 LLM 上下文

## 安全说明

- Shell 命令和文件操作限制在 `~/ai-agent-workspace` 目录
- 危险命令（rm -rf /、shutdown 等）被黑名单拦截
- 命令执行超时 30 秒

## 扩展指南

**添加新工具**：实现 `AgentTool` 接口并注册为 Spring Bean：

```java
@Component
public class MyTool implements AgentTool {
    @Override public String getName() { return "my_tool"; }
    @Override public String getDescription() { return "工具描述"; }
    @Override public Map<String, Object> getParametersSchema() { ... }
    @Override public String execute(Map<String, Object> args) { ... }
}
```

**切换模型**：修改 `application.yml` 中的 `glm.model`（如 `glm-5.3-flash`、`glm-4.5-air`）。
