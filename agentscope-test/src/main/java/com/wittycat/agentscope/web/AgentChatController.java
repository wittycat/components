package com.wittycat.agentscope.web;

import com.wittycat.agentscope.Example2_ToolCall.MathTools;
import com.wittycat.agentscope.Example2_ToolCall.WeatherTools;
import com.wittycat.agentscope.ModelFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.tool.Toolkit;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Agent 聊天接口:POST /api/agent/chat,SSE 流式返回。
 *
 * 【知识点】
 * 1. Spring MVC(webservlet 栈)原生支持返回 Flux<ServerSentEvent<T>>,框架会自动按
 *    text/event-stream 逐条推送给客户端——不需要手写 SseEmitter。
 * 2. Agent 实例是单例、线程安全的:内部按 sessionId 隔离状态,多个用户同时请求互不干扰
 *    (每个用户用自己的 sessionId 即可)。
 * 3. 这里只挑了 3 种对用户有意义的事件转成 SSE 文本;AgentScope 一共有 30 来种事件类型
 *    (io.agentscope.core.event.AgentEventType),做前端界面/监控时可以按需取用。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final ReActAgent agent;

    public AgentChatController() {
        // 和示例 2 一样:工具就是普通 Java 类,直接复用 WeatherTools / MathTools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTools());
        toolkit.registerTool(new MathTools());

        this.agent = ReActAgent.builder()
                .name("web-agent")
                .sysPrompt("你是一个网页聊天助手,回答简洁。涉及天气和计算时调用工具。")
                .model(ModelFactory.createGlmChatModel())
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    /** 请求体:message 必填;sessionId 可选,传相同值即可多轮对话 */
    public record ChatRequest(String message, String sessionId) {
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest req) {
        String sessionId = (req.sessionId() == null || req.sessionId().isBlank())
                ? "web-default"
                : req.sessionId();
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId("web-user")
                .build();

        return agent.streamEvents(req.message(), ctx)
                // 把事件流映射成 SSE 数据帧;不关心的事件返回空流即被丢弃
                // (注意 Reactor 的 map() 不允许返回 null,所以要这里用 concatMap)
                .concatMap(event -> {
                    if (event instanceof TextBlockDeltaEvent e) {
                        return Flux.just(frame(e.getDelta()));
                    }
                    if (event instanceof ToolCallStartEvent e) {
                        return Flux.just(frame("\n[调用工具: " + e.getToolCallName() + "]\n"));
                    }
                    if (event instanceof AgentResultEvent) {
                        return Flux.just(frame("\n[[DONE]]\n"));
                    }
                    return Flux.empty();
                });
    }

    private static ServerSentEvent<String> frame(String data) {
        return ServerSentEvent.builder(data).build();
    }
}
