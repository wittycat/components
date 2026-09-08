package com.wittycat.langchain4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * RAG 示例(7/8/10)共用的小工具:从 classpath 加载 txt 文档,并打上来源元数据。
 *
 * 【知识点】
 * 1. Document 是 LangChain4j 里"一份资料"的抽象 = 全文 + Metadata(来源、页码等)。
 *    元数据会随 TextSegment 一起被检索出来,生产中常用来做"引用出处"。
 * 2. 学习示例用 classpath 上的 txt;真实项目换 FileSystemDocumentLoader / PDF 解析器即可,
 *    后面的切分、向量化、检索代码完全不变。
 */
public final class RagDocs {

    private RagDocs() {
    }

    /** 读取 classpath 上的文本文件,包装成带来源元数据的 Document */
    public static Document load(String classpathLocation) {
        try (InputStream is = RagDocs.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (is == null) {
                throw new IllegalStateException("classpath 上找不到文档: " + classpathLocation);
            }
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Document.from(text, Metadata.from("source", classpathLocation));
        } catch (IOException e) {
            throw new UncheckedIOException("读取文档失败: " + classpathLocation, e);
        }
    }
}
