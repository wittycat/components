package com.agent.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器：将长文档切分为适合 RAG 检索的小块
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Invalid chunk parameters: chunkSize=" + chunkSize + ", overlap=" + overlap);
        }

        text = text.trim();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // 尽量在句号/换行处截断
            if (end < text.length()) {
                int breakPoint = findBreakPoint(text, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }

    private int findBreakPoint(String text, int start, int end) {
        for (int i = end - 1; i > start + (end - start) / 2; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？' || c == '.' || c == '!') {
                return i + 1;
            }
        }
        return end;
    }
}
