package com.wittycat.springai;

import org.springframework.ai.document.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * RAG 示例(7/8)共用的小工具:从 classpath 加载 txt 文档,包装成 Spring AI 的 Document。
 *
 * 【知识点】
 * 1. Document 是 Spring AI 里"一份资料"的抽象 = 全文文本 + Metadata(来源、页码等)。
 *    元数据会随切分后的片段一起入库和检索,生产中常用来做"引用出处"和按条件过滤。
 *    本示例在文本首部直接写来源注释,追求简单;TextReader/JSONReader 等内置 Reader
 *    会自动填 metadata(见 Example7 的做法)。
 * 2. 学习示例用 classpath 上的 txt;真实项目换成 PDF/HTML 解析器或文件系统读取即可,
 *    后面的切分、向量化、检索代码完全不变——这就是 ETL 管线抽象的价值。
 */
public final class RagDocs {

    private RagDocs() {
    }

    /** 读取 classpath 上的文本文件,包装成 Document(文本开头带来源说明) */
    public static Document load(String classpathLocation) {
        try (InputStream is = RagDocs.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (is == null) {
                throw new IllegalStateException("classpath 上找不到文档: " + classpathLocation);
            }
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new Document(text);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文档失败: " + classpathLocation, e);
        }
    }
}
