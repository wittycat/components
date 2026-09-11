# AI Agent 项目说明

## 项目简介

AI Agent 是一个基于大语言模型的智能体应用，后端使用 Spring Boot 框架，前端使用 Vue 3 构建。

## 核心功能

1. **流式对话**：通过 SSE（Server-Sent Events）实现实时流式输出，用户可以看到大模型逐字生成的过程。

2. **RAG 检索增强生成**：
   - 支持上传 .txt、.md 等文本文档到知识库
   - 文档自动分块并生成向量嵌入
   - 用户提问时自动检索最相关的知识片段
   - 将检索结果注入 Prompt，让回答更准确

3. **Tool Calling 工具调用**：
   - run_shell_command：在工作目录执行 Shell 命令
   - read_file：读取工作目录中的文件
   - write_file：写入文件到工作目录

4. **对话记忆**：
   - 所有对话自动保存到 MySQL
   - 加载最近 20 条消息作为上下文
   - 支持多轮连续对话

## 技术架构

- 后端：Spring Boot 3.2 + JPA + MySQL
- 前端：Vue 3 + Vite
- 大模型：智谱 GLM（OpenAI 兼容接口）
- 通信：REST API + SSE 流式

## 工作目录

工具操作的默认工作目录为项目根下的 workspace/ 目录，所有 Shell 命令和文件读写都限制在此目录内，确保安全性。

## 常见问题

Q: 如何更换大模型？
A: 修改 application.yml 中的 glm.model 配置项。

Q: RAG 检索不准确怎么办？
A: 确保已配置有效的 API Key 以启用向量检索；否则会降级为关键词匹配。

Q: 工具调用失败？
A: 检查项目根下的 workspace/ 目录是否存在（相对路径配置由后端按项目根解析并自动创建），以及命令是否被安全策略拦截。
