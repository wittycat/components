package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 示例 3:流式输出 —— 逐 token 推送,打字机效果的基础。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example3_Streaming
 *
 * 【知识点】
 * 1. call() 等全部生成完才返回;stream() 返回 Reactor 的 Flux<String>,
 *    每个元素是一个增量片段(token 或词块),首字延迟大幅降低,长回答体验完全不同。
 * 2. Flux 是响应式流:doOnNext 订阅每个片段,blockLast() 在纯 main() 里阻塞等待结束。
 *    在 Web 场景不需要 block——直接把 Flux 作为 Controller 返回值交给 Spring
 *    (见示例 9 的 SSE 接口,对比 langchain4j-test 手动回调 + SseEmitter 的写法)。
 * 3. .stream().content() 只取文本片段;.stream().chatResponse() 拿完整 ChatResponse 流,
 *    最后一个分片带 token 用量等元数据(需要统计时用它)。
 */
public class Example3_Streaming {

    public static void main(String[] args) {
        ChatClient chatClient = ChatClient.create(GlmModels.createChatModel());

        System.out.println("开始流式输出:");
        long start = System.currentTimeMillis();

        Flux<String> flux = chatClient.prompt()
                .user("用 200 字左右介绍一下 Spring AI 的 Advisor 机制。")
                .stream()
                .content();

        // main() 里用 blockLast() 等待流结束;每到一个片段立即打印,控制台就是打字机效果
        flux.doOnNext(token -> {
                    System.out.print(token);
                    System.out.flush();
                })
                .blockLast();

        System.out.printf("%n%n流式输出完成,耗时 %d ms(首片段早就打出来了,对比示例 1 的 call() 试试)%n",
                System.currentTimeMillis() - start);
    }
}
