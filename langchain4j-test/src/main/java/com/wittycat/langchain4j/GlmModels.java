package com.wittycat.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/**
 * 模型工厂:把 "OpenAI 协议 + 智谱 GLM 端点" 封装成三个工厂方法,全部示例共用。
 *
 * 【知识点】
 * 1. LangChain4j 的模型抽象是三个接口,厂商实现各写一个类:
 *      - ChatModel          一次性对话:发一整段请求,拿完整回复(示例 1/2/4/5/6/8/10);
 *      - StreamingChatModel 流式对话:逐 token 回调(示例 3 和 Web SSE);
 *      - EmbeddingModel     向量化:把文本变成 float[],语义检索的坐标(示例 7/8/10)。
 *    业务代码只面向接口编程,不关心底下是哪家厂商。
 * 2. 接入智谱 GLM 的方式:用 langchain4j-open-ai 实现 + 把 baseUrl 指到 GLM 的 OpenAI 兼容端点。
 *    OpenAI 兼容协议已经是事实标准,GLM/DeepSeek/Kimi/vLLM 都支持,所以"一套客户端代码,全厂商通用"。
 * 3. temperature 越低回答越确定(0 近似复述),越高越发散;maxOutputTokens 限制回复长度,
 *    防止失控输出——这两个是最常用的采样参数。
 */
public final class GlmModels {

    private GlmModels() {
    }

    /** 聊天模型,默认参数(temperature 0.7,适合大多数学习示例) */
    public static ChatModel createChatModel() {
        return createChatModel(0.7, 2048);
    }

    /** 聊天模型,可调采样参数 */
    public static ChatModel createChatModel(double temperature, int maxOutputTokens) {
        return OpenAiChatModel.builder()
                .baseUrl(GlmConfig.baseUrl())
                .apiKey(GlmConfig.resolveApiKey())
                .modelName(GlmConfig.model())
                .temperature(temperature)
                .maxTokens(maxOutputTokens)
                .build();
    }

    /** 流式聊天模型:逐 token 回调,示例 3 和 Web SSE 用 */
    public static StreamingChatModel createStreamingModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(GlmConfig.baseUrl())
                .apiKey(GlmConfig.resolveApiKey())
                .modelName(GlmConfig.model())
                .temperature(0.7)
                .maxTokens(2048)
                .build();
    }

    /** 向量模型:智谱 embedding-3,示例 7/8/10 的语义检索用 */
    public static EmbeddingModel createEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(GlmConfig.baseUrl())
                .apiKey(GlmConfig.resolveApiKey())
                .modelName(GlmConfig.embeddingModel())
                .build();
    }
}
