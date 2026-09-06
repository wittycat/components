package com.wittycat.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 示例 5:多智能体协作 —— 这才是"和直接调 API 拉开差距"的地方。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example5_MultiAgent
 *
 * 任务背景:开发一个"判断回文字符串"的 Java 工具方法。安排三个各司其职的 Agent:
 *   algo-expert(算法专员): 只讲算法思路和边界情况,不写代码
 *   code-expert(编码专员): 只输出 Java 代码,不解释
 *   tech-lead(技术负责人): 不自己干活,负责协调前两位,汇总最终交付
 *
 * 演示两种多智能体协作模式:
 *
 * 【Part 1 编排式】Java 代码当"导演",按固定顺序调用各 Agent,把上一步输出喂给下一步。
 *   理解要点:Agent 就是普通 Java 对象,可以直接组合——没有框架参与。
 *   适合:流程固定、步骤明确的场景(类 pipeline)。
 *
 * 【Part 2 委托式】把两个专员 Agent 包成 tech-lead 的"工具"(Agent-as-Tool),
 *   lead 收到任务后自己决定:先问谁、问什么、要不要追问,最后自己汇总。
 *   理解要点:这是 AgentScope 官方 SubAgentTool 的底层原理;协调逻辑从 Java 代码
 *   转移到了模型手里,流程不固定、需要"看情况办事"的任务用这种模式。
 *   适合:开放式任务——这也是裸调单次 API 永远做不到的事。
 */
public class Example5_MultiAgent {

    /** 专员们的独立线程池:在 lead 的事件流内部阻塞等待专员回复,必须跑在普通线程上,
     *  不能占用 Reactor 的调度线程(那里禁止 block()),所以显式开池子隔离 */
    private static final ExecutorService SPECIALIST_POOL = Executors.newFixedThreadPool(2);

    public static void main(String[] args) throws Exception {
        banner("Part 1 编排式:Java 代码当导演,固定流水线");
        part1Pipeline();

        banner("Part 2 委托式:tech-lead 自主决定咨询谁、问几次");
        part2Delegation();

        SPECIALIST_POOL.shutdown();
        SPECIALIST_POOL.awaitTermination(10, TimeUnit.SECONDS);
    }

    // ==================== Part 1:编排式 ====================
    private static void part1Pipeline() {
        ReActAgent algoExpert = specialistAgent("algo-expert");
        ReActAgent codeExpert = specialistAgent("code-expert");

        RuntimeContext ctxAlgo = RuntimeContext.builder().sessionId("p1-algo").userId("learner").build();
        RuntimeContext ctxCode = RuntimeContext.builder().sessionId("p1-code").userId("learner").build();

        // 第 1 步:先问算法专员要思路
        String idea = algoExpert.call("设计一个判断回文字符串的 Java 方法的算法思路,包括边界情况", ctxAlgo)
                .block().getTextContent();
        System.out.println("[1/2 算法专员的思路]\n" + idea + "\n");

        // 第 2 步:把思路作为上下文喂给编码专员,要代码
        String code = codeExpert.call("根据以下算法思路,实现判断回文的 Java 方法:\n" + idea, ctxCode)
                .block().getTextContent();
        System.out.println("[2/2 编码专员的代码]\n" + code);
    }

    // ==================== Part 2:委托式 ====================
    private static void part2Delegation() {
        // 1. 两个专员 Agent(和 Part 1 相同,职责由 sysPrompt 定义)
        ReActAgent algoExpert = specialistAgent("algo-expert");
        ReActAgent codeExpert = specialistAgent("code-expert");

        // 2. 关键一步:把专员"包成工具"注册给 tech-lead
        //    从此在 lead 眼里,"咨询算法专员"和"查天气"没有区别,都是可调用的工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new DelegationTools(algoExpert, codeExpert));

        ReActAgent techLead = ReActAgent.builder()
                .name("tech-lead")
                .sysPrompt("""
                        你是技术负责人,负责协调团队完成开发任务。你自己不写算法也不写代码,
                        必须通过工具咨询对应的专员:consult_algo_expert(算法思路)、
                        consult_code_expert(写代码)。先搞清思路再让编码专员动手,
                        最后基于两位专员的产出,汇总一份简短的最终交付说明(思路要点+代码)。""")
                .model(ModelFactory.createGlmChatModel())
                .toolkit(toolkit)
                .maxIters(6)
                .build();

        RuntimeContext ctx = RuntimeContext.builder().sessionId("p2-lead").userId("learner").build();

        String task = "团队任务:交付一个判断回文字符串的 Java 工具方法(忽略大小写和非字母数字字符)。";
        System.out.println(">>> 任务: " + task);
        System.out.println("--- 观察 tech-lead 的协调过程 ---\n");

        techLead.streamEvents(task, ctx)
                .doOnNext(Example5_MultiAgent::printEvent)
                .blockLast();
    }

    /**
     * 委托工具:每个方法内部持有并调用一个完整的 Agent。
     * 注意工具返回 String——专员的回复会作为"工具结果"回填给 tech-lead,
     * 这就是两个 Agent 之间传递信息的通道。
     */
    public static class DelegationTools {

        private final ReActAgent algoExpert;
        private final ReActAgent codeExpert;

        public DelegationTools(ReActAgent algoExpert, ReActAgent codeExpert) {
            this.algoExpert = algoExpert;
            this.codeExpert = codeExpert;
        }

        @Tool(name = "consult_algo_expert", description = "咨询算法专员:获取算法思路和边界情况分析,不产出代码")
        public String consultAlgoExpert(
                @ToolParam(name = "question", required = true, description = "要咨询算法专员的问题") String question) {
            System.out.println(">>> [tech-lead 委托] 算法专员: " + question + "\n");
            return ask(algoExpert, "algo-session", question);
        }

        @Tool(name = "consult_code_expert", description = "咨询编码专员:根据算法思路产出 Java 代码,不解释")
        public String consultCodeExpert(
                @ToolParam(name = "request", required = true, description = "给编码专员的需求,最好附上算法思路") String request) {
            System.out.println(">>> [tech-lead 委托] 编码专员: " + truncate(request) + "\n");
            return ask(codeExpert, "code-session", request);
        }

        /** 专员跑在独立线程池上,阻塞等待其回复(原因见 SPECIALIST_POOL 上的注释) */
        private String ask(ReActAgent specialist, String sessionId, String question) {
            try {
                return SPECIALIST_POOL.submit(() -> {
                    RuntimeContext ctx = RuntimeContext.builder().sessionId(sessionId).userId("tech-lead").build();
                    Msg reply = specialist.call(question, ctx).block();
                    return reply == null ? "(专员没有回复)" : reply.getTextContent();
                }).get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "咨询失败: " + e.getMessage();
            }
        }

        private static String truncate(String s) {
            return s.length() <= 80 ? s : s.substring(0, 80) + "...";
        }
    }

    /** 创建专员 Agent:每个专员 = 自己的 sysPrompt(职责)+ 同一个模型 */
    private static ReActAgent specialistAgent(String name) {
        String sysPrompt = switch (name) {
            case "algo-expert" -> "你是算法专员,只负责讲清算法思路和边界情况,禁止写出完整代码。回答不超过 150 字。";
            case "code-expert" -> "你是编码专员,只负责输出单个 Java 代码块,禁止任何解释性文字。";
            default -> throw new IllegalArgumentException(name);
        };
        return ReActAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model(ModelFactory.createGlmChatModel())
                .build();
    }

    /** lead 的事件流只打印关心的部分(同示例 2 的做法) */
    private static boolean thinkingStarted = false;

    private static void printEvent(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent e) {
            System.out.print(e.getDelta());
        } else if (event instanceof ToolCallStartEvent e) {
            if (thinkingStarted) {
                System.out.println();
                thinkingStarted = false;
            }
            System.out.println("(tech-lead 决定调用工具: " + e.getToolCallName() + ")");
        } else if (event instanceof AgentResultEvent e) {
            System.out.println("\n--- tech-lead 最终交付 ---\n" + e.getResult().getTextContent());
        }
    }

    private static void banner(String title) {
        System.out.println("\n==================================================");
        System.out.println("  " + title);
        System.out.println("==================================================\n");
    }
}
