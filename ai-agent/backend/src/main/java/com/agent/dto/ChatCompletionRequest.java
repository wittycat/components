package com.agent.dto;

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
public class ChatCompletionRequest {

    private String model;
    private List<ChatMessageDto> messages;
    private boolean stream;
    private List<ToolDefinition> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        private String type;
        private FunctionDef function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionDef {
            private String name;
            private String description;
            private Map<String, Object> parameters;
        }
    }
}
