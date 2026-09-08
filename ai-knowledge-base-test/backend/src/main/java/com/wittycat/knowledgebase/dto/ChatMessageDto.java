package com.wittycat.knowledgebase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageDto {

    private String role;
    private String content;

    @JsonProperty("tool_calls")
    private List<ToolCallDto> toolCalls;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallDto {
        private String id;
        private String type;
        private FunctionCall function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionCall {
            private String name;
            private String arguments;
        }
    }
}
