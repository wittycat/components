package com.wittycat.langchain4j;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模型层日志监听器:每次模型调用前后打一行日志,等价于 Spring AI 的 SimpleLoggerAdvisor。
 *
 * 【知识点】
 * 1. LangChain4j 没有 Advisor 链,对应的观察机制是 ChatModelListener——挂在模型上而不是调用链上,
 *    onRequest/onResponse/onError 三个回调都是 default 方法,按需覆写即可。
 * 2. 挂在模型上意味着"凡是经过这个模型的调用都会触发",包括 AiServices 内部组装完记忆/工具后的
 *    最终请求——所以日志里看到的消息列表,就是真正发给模型的完整内容(含历史消息)。
 *    在示例 5 里能直观看到:第二轮对话的日志里带着第一轮的历史。
 * 3. onRequest 和 onResponse 共享同一个 attributes() Map:请求时塞个 ID、响应时取出来,
 *    就能把一对请求/响应关联起来,并发调用时也能配对。
 * 4. 它只能观察、不能改写请求;Spring AI 的 Advisor 还能增改 prompt/记忆,这是两边设计思路的差异
 *    (Spring AI 把拦截+改写统一进一条链,LC4j 把"观察"和"编排"拆开)。
 * 5. 懒人替代:OpenAiChatModel.builder().logRequests(true).logResponses(true)
 *    直接打完整 HTTP 报文(INFO 级,api-key 头自动脱敏),不用写任何类;本类适合打结构化业务日志。
 */
public class SimpleLoggerChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(SimpleLoggerChatModelListener.class);

    @Override
    public void onRequest(ChatModelRequestContext context) {
        log.info("[ChatModel] 发送请求: messages={}", context.chatRequest().messages());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        log.info("[ChatModel] 收到回复: text='{}', tokenUsage={}",
                context.chatResponse().aiMessage().text(),
                context.chatResponse().tokenUsage());
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        log.error("[ChatModel] 模型调用失败", context.error());
    }
}
