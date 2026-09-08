package com.wittycat.langchain4j;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 示例 3:流式输出 —— 逐 token 回调,打字机效果的基础。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example3_Streaming
 *
 * 【知识点】
 * 1. ChatModel 是"一次性拿全量回复",StreamingChatModel 是"边生成边推送"。
 *    底层都是同一个 OpenAI 兼容端点,区别只是请求里 stream=true 与否。
 * 2. 回调风格:实现 StreamingChatResponseHandler 的三个方法——
 *      - onPartialResponse:每收到一小段(token)就回调一次,这是打字机效果的数据源;
 *      - onCompleteResponse:全部生成完,此时才能拿到完整响应(含 token 用量);
 *      - onError:出错(网络/鉴权/超时)。
 * 3. 流式调用是异步的:main 注册完回调就返回了,程序不会自己等模型生成完,
 *    所以这里用 CountDownLatch"挂住"主线程,完成后倒计时归零再退出。
 *    (Web 场景里这个"挂住"由服务器替你做,见示例 11 的 SseEmitter。)
 * 4. 什么场景必须流式?聊天界面的打字机效果、长回答的首字延迟优化、
 *    以及 SSE 推送(示例 11)。
 */
public class Example3_Streaming {

    public static void main(String[] args) throws InterruptedException {
        StreamingChatModel model = GlmModels.createStreamingModel();

        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger chunks = new AtomicInteger();
        long start = System.currentTimeMillis();

        System.out.println("流式回复(每个 token 到达就打印):");
        model.chat("用 150 字左右介绍 Java 21 的虚拟线程。", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                System.out.print(token);
                chunks.incrementAndGet();
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                long cost = System.currentTimeMillis() - start;
                System.out.printf("%n%n[完成] 共 %d 个片段,耗时 %d ms", chunks.get(), cost);
                if (response.tokenUsage() != null) {
                    System.out.printf(",输出 %d token", response.tokenUsage().outputTokenCount());
                }
                done.countDown();
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("\n[出错] " + error.getMessage());
                done.countDown();
            }
        });

        // 主线程等流式回调跑完再退出
        done.await();
    }
}
