package com.wittycat.springaialibaba.web;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.wittycat.springaialibaba.SupportRoutingGraph;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 示例 6 的接口:把"客服意图路由图"(示例 4 同款)的执行过程按节点流式推给浏览器。
 *
 * 【知识点】
 * 1. CompiledGraph.stream() 返回 Flux&lt;NodeOutput&gt;:每完成一个节点吐一帧(节点粒度),
 *    区别于 ChatClient.stream() 的 token 粒度 —— 工作流场景关心"走到哪一步了",这正是 SSE 的用武之地。
 * 2. NodeOutput 携带节点名 + 执行后的全量状态快照,取关心的字段(category/answer)组帧即可;
 *    配合 RunnableConfig.threadId,同 sessionId 多次请求还能接上上次的状态(需配 Saver,本例省略)。
 * 3. 测试: curl -N "localhost:8085/support/stream?message=账单多扣了9.9元怎么退款"
 *    (-N 关闭 curl 缓冲才能看到逐帧推送;中文参数记得 URL 编码)。
 */
@RestController
public class SupportController {

    private final CompiledGraph supportGraph;

    public SupportController(ChatModel chatModel) {
        // ChatModel 由 starter 按 spring.ai.dashscope.* 自动配置;图只建一次,所有请求共用(无状态)
        try {
            this.supportGraph = SupportRoutingGraph.build(ChatClient.create(chatModel)).compile();
        } catch (Exception e) {
            throw new IllegalStateException("编译 support-routing 图失败", e);
        }
    }

    @GetMapping(value = "/support/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String message,
                                                @RequestParam(defaultValue = "web") String sessionId) {
        return supportGraph.stream(
                        java.util.Map.of(SupportRoutingGraph.QUESTION, message),
                        RunnableConfig.builder().threadId(sessionId).build())
                .map(this::toFrame)
                .onErrorResume(e -> Mono.just(ServerSentEvent.<String>builder()
                        .event("error").data("图执行失败: " + e.getMessage()).build()));
    }

    /** 把一帧 NodeOutput 组装成 SSE:event=节点名,data=该节点执行后的关键状态 */
    private ServerSentEvent<String> toFrame(NodeOutput output) {
        OverAllState state = output.state();
        StringBuilder data = new StringBuilder();
        Object category = state.value(SupportRoutingGraph.CATEGORY).orElse(null);
        if (category != null) {
            data.append("category=").append(category).append(' ');
        }
        state.value(SupportRoutingGraph.ANSWER).ifPresent(a -> data.append("answer=").append(a));
        if (data.isEmpty()) {
            data.append("(节点执行完毕)");
        }
        return ServerSentEvent.<String>builder()
                .event(output.node())
                .data(data.toString().trim())
                .build();
    }
}
