package com.wittycat.springai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 模型工厂:把 "OpenAI 协议 + 智谱 GLM 端点" 封装成工厂方法,纯 main() 示例共用。
 *
 * 【知识点】
 * 1. Spring AI 有两套拿到模型的方式,本模块各演示一半:
 *      - 手动构建(本类):OpenAiApi(协议客户端)→ OpenAiChatModel(实现 ChatModel 接口),
 *        不启动 Spring,示例聚焦 API 本身;
 *      - 自动配置(Web 示例):spring.ai.openai.* 配置 + starter,容器里直接注入
 *        ChatClient.Builder / ChatModel / EmbeddingModel,一行配置代码都不用写。
 * 2. Spring AI 的模型抽象是 ChatModel(一次性)/StreamingChatModel(流式)/EmbeddingModel(向量化),
 *    业务代码面向接口编程;OpenAI 兼容协议是事实标准,GLM/DeepSeek/Kimi/vLLM 都支持,
 *    所以"一套客户端代码,全厂商通用"。
 * 3. temperature 越低回答越确定(0 近似复述),越高越发散;maxTokens 限制回复长度,
 *    防止失控输出——这两个是最常用的采样参数,通过 OpenAiChatOptions 传入。
 */
public final class GlmModels {

    private GlmModels() {
    }

    /** 聊天模型,默认参数(temperature 0.7,适合大多数学习示例) */
    public static ChatModel createChatModel() {
        return createChatModel(0.7, 2048);
    }

    /** 聊天模型,可调采样参数 */
    public static ChatModel createChatModel(double temperature, int maxTokens) {
        return OpenAiChatModel.builder()
                .openAiApi(createApi())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(GlmConfig.model())
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();
    }

    /** 向量模型:智谱 embedding-3,示例 7/8 的语义检索用 */
    public static EmbeddingModel createEmbeddingModel() {
        return new OpenAiEmbeddingModel(createApi(), MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(GlmConfig.embeddingModel())
                        .build());
    }

    /**
     * OpenAI 协议客户端。
     * 【易踩坑】Spring AI 默认在 base-url 后拼 /v1/chat/completions 和 /v1/embeddings,
     * 而 GLM 的 OpenAI 兼容端点路径是 /v4/chat/completions(不带 /v1 前缀),
     * 不改这两个 path 会得到 404。DeepSeek/Kimi 等同理,接入时都要核对路径。
     */
    private static OpenAiApi createApi() {
        return OpenAiApi.builder()
                .baseUrl(GlmConfig.baseUrl())
                .apiKey(GlmConfig.resolveApiKey())
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();
    }
}
