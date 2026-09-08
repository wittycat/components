package com.wittycat.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {

    public enum Type {
        CONTENT, TOOL_CALL, TOOL_RESULT, RAG_CONTEXT, CONVERSATION_CREATED, DONE, ERROR
    }

    private Type type;
    private String content;
    private String toolName;
    private String toolResult;
}
