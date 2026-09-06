package com.wittycat.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

/**
 * 示例 2:给 Agent 装上工具(Tool Calling),亲眼观察 ReAct 循环。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example2_ToolCall
 *
 * 【知识点】
 * 1. 一个"工具"就是普通 Java 类上的一个 @Tool 注解方法。AgentScope 通过反射读取方法签名,
 *    自动生成 JSON Schema 描述发给大模型,模型决定"要不要调、传什么参数"。
 *    @ToolParam 描述每个参数;工具方法返回 String(会作为工具结果回填给模型)。
 * 2. ReAct 循环全过程(本例的事件流里都能看到):
 *      模型思考(Thinking) → 决定调用工具(ToolCallStart)
 *      → AgentScope 真正执行工具方法 → 结果回填给模型
 *      → 模型继续思考 → ... → 输出最终回答(AgentResult)
 * 3. streamEvents() 返回 Flux<AgentEvent> 事件流,这是 AgentScope 观测/集成的主流方式,
 *    示例 4 的 SSE 接口也是基于它。
 * 4. maxIters 限制循环轮数,防止模型反复调工具停不下来。
 */
public class Example2_ToolCall {

    public static void main(String[] args) {
        // 1. 工具箱:一个 Toolkit 可以注册多个工具类
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTools());
        toolkit.registerTool(new MathTools());

        // 2. 创建带工具的 Agent
        ReActAgent agent = ReActAgent.builder()
                .name("tool-agent")
                .sysPrompt("你是一个助手,回答天气和计算问题时必须调用提供的工具,不要自己编造结果。")
                .model(ModelFactory.createGlmChatModel())
                .toolkit(toolkit)
                .maxIters(5)
                .build();

        RuntimeContext ctx = RuntimeContext.builder().sessionId("tool-demo").userId("learner").build();

        // 这个问题必须"查两次天气 + 做一次减法"才能回答,刚好逼出完整的 ReAct 循环
        String question = "北京和上海今天哪个城市温度更高?高多少度?";
        System.out.println(">>> 用户: " + question);
        System.out.println("--- Agent 事件流(观察 思考→调工具→回答)---");

        // 3. 流式执行:逐个事件消费,blockLast() 等待整个流程结束
        agent.streamEvents(new UserMessage(question), ctx)
                .doOnNext(Example2_ToolCall::printEvent)
                .blockLast();
    }

    /** 把关心的事件翻译成可读日志;其余事件(如块开始/结束)直接忽略 */
    private static boolean thinkingStarted = false;

    private static void printEvent(AgentEvent event) {
        if (event instanceof ThinkingBlockDeltaEvent e) {
            // GLM 是思考模型,思考增量单独归类(没有思考内容时这个分支不会触发)
            if (!thinkingStarted) {
                System.out.print("[思考] ");
                thinkingStarted = true;
            }
            System.out.print(e.getDelta());
        } else if (event instanceof TextBlockDeltaEvent e) {
            // 最终回答的增量文本(思考结束后换行,让输出更好读)
            if (thinkingStarted) {
                System.out.println();
                thinkingStarted = false;
            }
            System.out.print(e.getDelta());
        } else if (event instanceof ToolCallStartEvent e) {
            if (thinkingStarted) {
                System.out.println();
                thinkingStarted = false;
            }
            System.out.println("\n>>> 模型决定调用工具: " + e.getToolCallName());
        } else if (event instanceof AgentResultEvent e) {
            // 整个 ReAct 循环结束,Result 就是最终回复消息
            System.out.println();
            System.out.println("--- 最终回答: " + e.getResult().getTextContent());
        }
    }

    /**
     * 工具类 1:天气查询(模拟数据——重点看"模型怎么调用工具",不是数据本身)。
     * 真实项目里方法体里调天气 API 即可,对 Agent 来说没有任何区别。
     */
    public static class WeatherTools {

        @Tool(name = "get_weather", description = "查询指定城市当前的天气和气温")
        public String getWeather(
                @ToolParam(name = "city", required = true, description = "城市名,例如:北京") String city) {
            System.out.println("    [工具执行] get_weather(city=" + city + ")");
            return switch (city) {
                case "北京" -> "晴,26℃";
                case "上海" -> "多云,31℃";
                default -> city + ":晴,25℃";
            };
        }
    }

    /** 工具类 2:计算器(展示多参数工具) */
    public static class MathTools {

        @Tool(name = "subtract", description = "计算 a-b 的差,用于比较两个数值的差值")
        public String subtract(
                @ToolParam(name = "a", required = true, description = "被减数") double a,
                @ToolParam(name = "b", required = true, description = "减数") double b) {
            System.out.println("    [工具执行] subtract(" + a + " - " + b + ")");
            return String.valueOf(a - b);
        }
    }
}
