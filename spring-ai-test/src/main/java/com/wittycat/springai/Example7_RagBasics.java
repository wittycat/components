package com.wittycat.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 示例 7:RAG 基础 —— 切分 → 向量化入库 → 手动检索 → 拼上下文,三个环节亲手过一遍。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.springai.Example7_RagBasics
 *
 * 【执行流程】
 *
 *  main()
 *   │
 *   ├─ ① 创建向量模型 embeddingModel(embedding-3)
 *   │
 *   ├─ ② 热身:embed("什么是向量检索?") ──HTTP──→ float[2048]      感受"文字→向量"原语
 *   │
 *   ├─ ③ 建库:SimpleVectorStore(内存 Map,暴力扫,无 ANN 索引)
 *   │
 *   ├─ ④ 切块:RagDocs.load(知识库txt) → TokenTextSplitter → 约 10 个 Document
 *   │
 *   ├─ ⑤ 入库:vectorStore.add(chunks)
 *   │        └─ 内部循环每块 embed(doc) ──HTTP──→ 存(id,原文,元数据,向量)  ← N 次调用
 *   │
 *   ├─ ⑥ 检索:vectorStore.similaritySearch(问题, topK=3, 阈值0.3)
 *   │        ├─ embed(问题) ──HTTP──→ float[2048]                          ← 1 次调用
 *   │        ├─ 全库逐块算余弦相似度(暴力扫 O(n))
 *   │        └─ 过滤低于 0.3 → 按分数降序 → 取前 3 块
 *   │
 *   ├─ ⑦ 拼上下文:命中片段按分数从高到低,分隔线相连
 *   │
 *   └─ ⑧ 生成:ChatClient(glm-5.3-flash)
 *            .system("仅根据资料回答") + .user(资料+问题)
 *            → call() → 模型回答                                          ← 这里才用 ChatModel
 *
 * 【知识点】
 * 1. 模型只知道训练数据里的东西,你的私有文档它没见过;直接塞全文又会撑爆上下文窗口。
 *    RAG(检索增强生成)的思路:把文档切成片段、向量化存起来,提问时只检索最相关的
 *    几段塞进上下文——省 token 又准。
 * 2. 入库是 Spring AI 的 ETL 管线:Document(文档)→ TokenTextSplitter(按 token 切分的
 *    DocumentTransformer)→ VectorStore.add()(内部调用 EmbeddingModel 向量化后存储)。
 * 3. 检索:similaritySearch 按余弦相似度取 Top-N,SearchRequest 可设 topK 和
 *    similarityThreshold(低于阈值的结果直接过滤,是治幻觉的第一道闸)。
 *    Document.getScore() 返回相似度分数,越接近 1 越相关。
 * 4. SimpleVectorStore 是内存参考实现(重启即失);生产换 PGVector / Milvus / Redis,
 *    只是换一个 VectorStore Bean,检索代码一行不改。
 * 5. 本例最后一步手动拼提示词,把"资料 + 问题"一起发出去;示例 8 会把这个动作
 *    交给 QuestionAnswerAdvisor 自动完成。
 *
 * 【常见问答】
 * Q1:vectorStore.add() 里调用 Embedding 模型了吗?
 * A1:调用了。SimpleVectorStore.doAdd() 内部 for 循环逐块 embeddingModel.embed(document),
 *    每块一次 HTTP 调用,向量连同原文、元数据一起存入内存 Map;key 错或断网会在 add() 当场抛异常。
 * Q2:similaritySearch() 里问题转成向量了吗?
 * A2:转了。doSimilaritySearch() 第一步就是 embeddingModel.embed(query),每次检索恰好 1 次
 *    调用;之后全库逐条算余弦、按阈值过滤、降序排序、截取 topK(对照本类的 0.3 和 3)。
 * Q3:ANN 索引是向量数据库的索引吗?
 * A3:是核心索引的统称(HNSW、IVF-PQ、DiskANN 等),思路是"用少量召回损失换百倍速度";
 *    向量库里另有管元数据过滤的普通标量索引。本类用的 SimpleVectorStore 没建 ANN 索引,
 *    属于全库暴力扫的 FLAT 模式——结果 100% 精确,只是数据量大了会慢。
 * Q4:文字转向量需要调用 LLM 吗?
 * A4:需要调模型,但不是 ChatModel,而是专门的 Embedding 模型(本类 embedding-3),
 *    便宜 1~2 个数量级且不可逆。一次 RAG 问答的成本:入库 N 次 embedding(一次性)
 *    + 提问 1 次 embedding(可忽略)+ 1 次 ChatModel 生成(大头)。
 *
 * @author  javachenxun
 * @date 2026/09/08
 */
public class Example7_RagBasics {

    /**
     * 程序入口,运行方式见类注释。
     */
    public static void main(String[] args) {
        // 向量模型(智谱 embedding-3,OpenAI 协议):把文本变成向量,RAG 的检索"坐标"全靠它
        EmbeddingModel embeddingModel = GlmModels.createEmbeddingModel();

        // ========== 热身:先直观感受"向量化"这个原语 ==========
        // embed(String):一段文本 → 一个 float 向量;语义相近的文本,向量距离也近
        float[] vector = embeddingModel.embed("什么是向量检索?");
        // 打印维度和前 5 个分量,让"文本变成了数字"看得见摸得着
        System.out.printf("embedding-3 把文本变成 %d 维向量,前 5 维: %s...%n",
                vector.length, java.util.Arrays.toString(java.util.Arrays.copyOf(vector, 5)));

        // ========== 第 1 步:入库(切分 → 向量化 → 存储,即 ETL 管线) ==========
        // 内存向量库:builder 传入向量模型,后续 add() 会自动向量化;重启即失,生产换 PGVector/Milvus
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        /*
         * TokenTextSplitter 六个参数 = 切块流水线的 6 个控制点(基于 CL100K 分词器,中文约 1 字 ≈ 0.5~1.5 token):
         * ① 150    chunkSize      每块目标 token 数——主旋钮:块小检索准但断章取义,块大上下文全但稀释相关度;
         * ② 40     minChunkSizeChars  标点截断点的"最小位置"(易误解:不是每块至少 40 字)——
         *          窗口里从后往前找标点,只有位置超过第 40 字符才在那里下刀,否则保持整窗,防切出碎块;
         * ③ 5      minChunkLengthToEmbed  碎片丢弃线:切出不足 5 字符的块直接扔掉,不入库不向量化;
         * ④ 10000  maxNumChunks   熔断上限:超出即停,剩余内容会合并成"最后一大块"(防失控,非调优项);
         * ⑤ true   keepSeparator  保留换行/标点本身;代码、Markdown 等格式即语义的内容必须 true;
         * ⑥ ['\n'] punctuationMarks 合法下刀点:只在段落边界切。注意默认值 [.?! \n] 全是英文标点、
         *          没有中文句号——中文长段落建议加上 '。',如 List.of('\n', '。')。
         *
         * 举例(chunkSize=50、minChunkSizeChars=40):130 token 的文档,\n 恰在第 45 字符处 →
         *   第 1 刀落在 45 字符处(45 > 40 允许截断),第 1 块约 45 字符、消耗约 38 token;
         *   剩余 92 token > 50,继续切;最后一块(≤50 token)不再找标点、整段输出;
         *   若某块切出来只有 3 个字符 → 被 ③ 丢弃。
         */
        List<Document> chunks = new TokenTextSplitter(150, 40, 5, 10000, true,
                java.util.List.of('\n')).split(
                        // classpath 上的知识库文本 → Document(一份资料 = 全文 + 元数据,见 RagDocs)
                        List.of(RagDocs.load("rag/knowledge-springai.txt")));
        // 入库:内部对每个 chunk 调向量模型转向量后保存——ETL 管线的最后一步
        vectorStore.add(chunks);
        // 展示切块数:切分粒度是 RAG 调优的第一杠杆(太碎丢上下文,太大稀释相关度)
        System.out.println("知识库切成了 " + chunks.size() + " 段");

        // ========== 第 2 步:检索 ==========
        // 用户问题:注意问题本身也会被向量化,再和库里所有 chunk 算余弦相似度
        String question = "Spring AI 的 Advisor 是什么机制?";
        // 相似度检索:返回最相关的若干段;低于阈值(0.3)的直接丢弃——治幻觉的第一道闸
        List<Document> hits = vectorStore.similaritySearch(
                // SearchRequest 组装检索条件:问题 + 取前 3 段 + 阈值 0.3
                // (embedding-3 的余弦分整体偏低,阈值设 0.5 会把正确答案也滤掉)
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .similarityThreshold(0.3)
                        .build());

        System.out.println("\n问题: " + question);
        System.out.println("检索到的资料(分数越接近 1 越相关):");
        // 逐段打印相似度分数和片段开头:getScore() 即"这段资料和问题的相关度"
        for (Document hit : hits) {
            System.out.printf("  [%.4f] %s...%n", hit.getScore(),
                    abbreviate(hit.getText(), 40));
        }

        // ========== 第 3 步:把"资料 + 问题"拼成提示词发给模型 ==========
        // 检索片段按分数从高到低拼接,分隔线隔开,作为"资料"准备塞进 user 消息
        String context = hits.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        String answer = ChatClient.create(GlmModels.createChatModel()).prompt()
                // 系统提示词约束"只看资料、没有就说不知道"——防幻觉的第二道闸(第一道是检索阈值)
                .system("仅根据提供的资料回答问题;资料里没有的信息就直接说不知道,不要编造。")
                // user 消息 = 检索到的资料 + 原问题:模型只见这几段而非全文,省 token 又准
                .user("资料:\n" + context + "\n\n问题:" + question)
                .call()
                .content();

        System.out.println("\n模型回答:\n" + answer);
    }

    /**
     * 压缩空白并截断到 max 字符:纯为控制台输出整洁,与 RAG 逻辑无关。
     */
    private static String abbreviate(String text, int max) {
        // 连续空白(含换行)压成单个空格,免得原文档的换行打断单行输出
        String oneLine = text.replaceAll("\\s+", " ");
        // 超长则截断并补省略号
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
