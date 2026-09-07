package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.GraphInput.args;

/**
 * 示例 4:Checkpoint —— 让图拥有"跨调用记忆"。
 *
 * 运行方式:
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example4_Checkpoint
 *
 * 【知识点】
 * 1. 不带 Checkpoint 的图是"无状态"的:每次 invoke 都从零开始,上一次的结果不会留下。
 *    给 compile() 传入 CheckpointSaver 后,图在每一步节点执行后都会把状态快照存下来,
 *    同一个 threadId 的下一次调用会自动从上次的状态继续——这就是"会话记忆"的实现原理。
 * 2. MemorySaver 是内置的内存版存储(重启即失),生产上可以换 postgres / mysql 等持久化实现,
 *    代码里只需要换 compile 时传入的 saver,图定义一行都不用改。
 * 3. RunnableConfig.builder().threadId("...") 用来标识"这一次运行属于哪个会话":
 *      - 同一个 threadId:接着上次的状态跑(聊天记忆就是这么来的);
 *      - 不同 threadId:互相隔离(每个用户一个 threadId 即可)。
 * 4. getStateHistory(config) 能取到这个线程的每一步状态快照(StateSnapshot),
 *    每个快照都能看到"当时的状态 + 下一个要执行的节点"。这也是调试和时间旅行(回放)的基础。
 * 5. 本例的 bot 节点故意不用 LLM:约定第一条消息是自我介绍("我叫X"),后续轮次靠它作答,
 *    纯 Java 就能验证"记忆确实跨调用生效了"。
 */
public class Example4_Checkpoint {

    static class ChatState extends AgentState {
        ChatState(Map<String, Object> data) {
            super(data);
        }

        /** messages 声明为 appender 通道,每轮对话追加,天然就是"聊天记录" */
        List<String> messages() {
            return value("messages", List.of());
        }
    }

    public static void main(String[] args) throws Exception {
        var workflow = new StateGraph<>(Map.<String, Channel<?>>of("messages", Channels.appender(ArrayList::new)),
                        ChatState::new)
                .addNode("user", AsyncNodeAction.node_async(state ->
                        Map.of("messages", List.of(state.value("input", "")))))
                .addNode("bot", AsyncNodeAction.node_async(state -> {
                    // 极简"记忆":约定第一条消息是自我介绍("我叫X"),后续轮次靠它作答
                    List<String> history = state.messages();
                    String name = (history.isEmpty() || !history.get(0).startsWith("我叫"))
                            ? "(你还没告诉我)"
                            : history.get(0).substring(2);
                    String reply = "bot: 你好," + name + "!有什么可以帮你?";
                    System.out.println("[bot] " + reply);
                    return Map.of("messages", List.of(reply));
                }))
                .addEdge(StateGraph.START, "user")
                .addEdge("user", "bot")
                .addEdge("bot", StateGraph.END);

        // 关键点:compile 时挂上 MemorySaver
        var graph = workflow.compile(CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .build());

        // 同一个 threadId = 同一个会话
        var config = RunnableConfig.builder().threadId("conv-1").build();

        System.out.println(">>> 第一轮:告诉它名字");
        graph.invoke(args(Map.of("input", "我叫小明")), config);

        System.out.println("\n>>> 第二轮:问它名字(不带 Checkpoint 的话它不可能知道)");
        graph.invoke(args(Map.of("input", "我叫什么名字?")), config);

        System.out.println("\n>>> 换一个 threadId(新会话),再问一个新问题");
        var config2 = RunnableConfig.builder().threadId("conv-2").build();
        graph.invoke(args(Map.of("input", "今天天气怎么样?")), config2);

        // 查看会话 conv-1 的状态快照历史
        System.out.println("\nconv-1 的状态快照历史:");
        for (StateSnapshot<ChatState> snap : graph.getStateHistory(config)) {
            System.out.printf("  快照@节点[%s] messages=%s%n", snap.node(), snap.state().messages());
        }
    }
}
