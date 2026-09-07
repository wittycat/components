package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;

import java.util.Map;

/**
 * 示例 5:流式执行 —— 三种消费 NodeOutput 的方式。
 *
 * 运行方式:
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example5_Streaming
 *
 * 【知识点】
 * 1. stream() 返回 AsyncGenerator<NodeOutput<S>>,它实现了 Iterable:
 *      - 增强 for:同步、逐节点消费(最直观);
 *      - forEachAsync(...):异步消费,返回 CompletableFuture,不阻塞当前线程,
 *        配合 thenRun/join 就是"后台跑图,完成后回调"的用法;
 *      - 生成器还能 map/filter(AsyncGenerator 自带),适合把节点输出转成 SSE 推给前端。
 * 2. StreamMode 决定每个 NodeOutput 里装的是什么:
 *      - VALUES(默认):该节点执行后的"完整状态"(本例 state.data() 全量打印);
 *      - SNAPSHOTS:输出 StateSnapshot,在状态之外还带 next()(下一个要执行的节点)、
 *        config()(可追溯到 threadId/checkpoint),适合做进度展示和断点调试。
 *    切换方式是改调 streamSnapshots()(内部自动把模式置为 SNAPSHOTS);
 *    注意 StateSnapshot 由 checkpoint 生成,compile 时必须挂 CheckpointSaver。
 * 3. 顺带一提:stream() 里元素的实际类型随模式变化,SNAPSHOTS 模式下是 StateSnapshot,
 *    需要时用 instanceof / 强转取用。
 */
public class Example5_Streaming {

    static class MyState extends AgentState {
        MyState(Map<String, Object> data) {
            super(data);
        }
    }

    public static void main(String[] args) throws Exception {
        var workflow = new StateGraph<>(MyState::new)
                .addNode("step1", AsyncNodeAction.node_async(state -> Map.of("step1", "完成")))
                .addNode("step2", AsyncNodeAction.node_async(state -> Map.of("step2", "完成")))
                .addEdge(StateGraph.START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", StateGraph.END);

        // 挂上 MemorySaver:快照模式的 StateSnapshot 由 checkpoint 生成,没有 saver 时
        // streamSnapshots() 也只能退化为普通 NodeOutput(这是 1.8.x 的实现细节)
        CompiledGraph<MyState> graph = workflow.compile(CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .build());
        Map<String, Object> inputs = Map.of();

        // ── 方式一:VALUES 模式 + 同步 for(默认模式)──────────────────────
        System.out.println("① VALUES 模式(for 循环):每步输出执行后的完整状态");
        for (NodeOutput<MyState> out : graph.stream(inputs)) {
            System.out.println("   [" + out.node() + "] " + out.state().data());
        }

        // ── 方式二:快照模式:streamSnapshots() 产出 StateSnapshot,多出 next() 信息 ──
        // 注意:__END__ 输出的是普通 NodeOutput,所以生产代码里要用 instanceof 判断,不要直接强转。
        System.out.println("\n② 快照模式(streamSnapshots):StateSnapshot 还告诉你下一个节点是谁");
        var snapshotConfig = RunnableConfig.builder().threadId("stream-demo").build();
        for (NodeOutput<MyState> out : graph.streamSnapshots(inputs, snapshotConfig)) {
            if (out instanceof StateSnapshot<MyState> snap) {
                System.out.println("   [" + snap.node() + "] next=" + (snap.next() == null ? "<END>" : snap.next()));
            } else {
                System.out.println("   [" + out.node() + "](普通 NodeOutput,无 next 信息)");
            }
        }

        // ── 方式三:forEachAsync 异步消费 ───────────────────────────────
        System.out.println("\n③ forEachAsync:主线程继续干别的,图在后台执行");
        var future = graph.stream(inputs).forEachAsync(out ->
                System.out.println("   [异步回调] " + out.node() + " 执行完"));
        System.out.println("   (主线程没有阻塞,正在做其他事……)");
        future.join(); // 等图跑完再退出
        System.out.println("   图执行完毕");

        // 演示程序直接退出:图执行器/生成器持有非守护线程,不退出 JVM 会一直挂着
        System.exit(0);
    }
}
