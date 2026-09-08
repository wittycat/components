package com.wittycat.springaialibaba;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;

/**
 * DashScope 模型工厂:CLI 示例(不启动 Spring)手动构建 ChatModel,与 spring-ai-test 的 GlmModels 对应。
 *
 * 【知识点】
 * 1. DashScopeChatModel 实现的就是 Spring AI 的 ChatModel/StreamingChatModel 接口 —— Spring AI Alibaba
 *    是"扩展"而不是"另起炉灶":spring-ai-test 里学的 ChatClient、Advisor、@Tool、entity() 在这边原样能用。
 * 2. 与 OpenAI 兼容直连的本质差异:
 *      - 直连(spring-ai-test 的做法):只有 OpenAI 协议语义,拿不到厂商特有能力;
 *      - DashScope 原生(本模块):暴露 DashScopeChatOptions 全部参数(思考模式/联网搜索/多模态等,
 *        见示例 2),embedding/rerank/语音/图像模型也是全家桶(本模块只演示 chat,其余只换 Builder)。
 * 3. Web 示例不经过本类:starter 自动配置会读 spring.ai.dashscope.* 帮你把同样的 Bean 装进容器。
 */
public final class DashScopeModels {

    private DashScopeModels() {
    }

    /** 构建 DashScope 聊天模型:端点 + 默认参数(model/temperature)都来自 DashScopeConfig */
    public static DashScopeChatModel createChatModel() {
        DashScopeApi api = DashScopeApi.builder()
                .apiKey(DashScopeConfig.resolveApiKey())
                .baseUrl(DashScopeConfig.baseUrl())
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(DashScopeConfig.model())
                        .temperature(0.7)
                        .build())
                .build();
    }
}
