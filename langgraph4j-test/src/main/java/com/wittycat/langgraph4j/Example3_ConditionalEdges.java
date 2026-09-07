package com.wittycat.langgraph4j;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * 示例 3:条件边(Conditional Edge)与循环 —— 图不只是"直线",还能分支和回环。
 *
 * 运行方式:
 *   mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langgraph4j.Example3_ConditionalEdges
 *
 * 【知识点】
 * 1. addConditionalEdges(源节点, 路由函数, 路由表):
 *      - 路由函数(EdgeAction):读当前状态,返回一个字符串(路由 key);
 *      - 路由表:Map<路由key, 目标节点>,把字符串翻译成下一个节点。
 *    它取代了普通边 addEdge,让"下一步去哪"由运行时状态决定——这是所有 Agent
 *    (判断该调工具还是该回答)的核心机制,后面示例 9、10 全靠它。
 * 2. 图里可以"画圈":coach 执行完再回到 grader,形成循环。
 *    循环 + 条件边 = "不达标就一直重试"的审批/自愈/ReAct 模式骨架。
 *    注意:循环必须有退出条件(本例是分数达标),否则要靠 recursionLimit 兜底防止死循环。
 * 3. score / attempts 没在 schema 里声明 Channel,所以是默认的"后写覆盖"语义,
 *    coach 返回的分数会直接覆盖旧分数——和示例 2 的 appender 追加语义对比着理解。
 */
public class Example3_ConditionalEdges {

    static class MyState extends AgentState {
        MyState(Map<String, Object> data) {
            super(data);
        }

        int score() {
            return value("score", 0);
        }

        int attempts() {
            return value("attempts", 0);
        }
    }

    public static void main(String[] args) throws Exception {
        var workflow = new StateGraph<>(MyState::new)
                // grader:模拟判卷。第一遍 attempts=0 得 40 分,补课后每次 +35
                .addNode("grader", AsyncNodeAction.node_async(state -> {
                    int score = 40 + state.attempts() * 35;
                    System.out.printf("[grader] 第 %d 次考试,得分 %d%n", state.attempts() + 1, score);
                    return Map.of("score", score);
                }))
                // coach:补课,考试次数 +1,然后回到 grader 再考
                .addNode("coach", AsyncNodeAction.node_async(state -> {
                    System.out.println("[coach] 没及格,安排补课……");
                    return Map.of("attempts", state.attempts() + 1);
                }))
                // pass:及格后的收尾节点
                .addNode("pass", AsyncNodeAction.node_async(state -> {
                    System.out.println("[pass] 恭喜通过!共考了 " + (state.attempts() + 1) + " 次");
                    return Map.of();
                }))
                .addEdge(StateGraph.START, "grader")
                // 条件边:根据 score 决定去 pass 还是去 coach
                .addConditionalEdges("grader",
                        AsyncEdgeAction.edge_async(state -> state.score() >= 60 ? "及格" : "不及格"),
                        Map.of("及格", "pass", "不及格", "coach"))
                .addEdge("coach", "grader")
                .addEdge("pass", StateGraph.END);

        var graph = workflow.compile();

        System.out.println("stream 执行轨迹(注意 coach→grader 的循环):");
        for (NodeOutput<MyState> out : graph.stream(Map.of())) {
            System.out.printf("  -> 节点[%s]%n", out.node());
        }
    }
}
