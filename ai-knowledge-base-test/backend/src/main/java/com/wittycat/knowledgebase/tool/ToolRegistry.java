package com.wittycat.knowledgebase.tool;

import com.wittycat.knowledgebase.config.AgentConfig;
import com.wittycat.knowledgebase.dto.ChatCompletionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<AgentTool> tools;

    public List<ChatCompletionRequest.ToolDefinition> getToolDefinitions() {
        return tools.stream().map(tool -> ChatCompletionRequest.ToolDefinition.builder()
                .type("function")
                .function(ChatCompletionRequest.ToolDefinition.FunctionDef.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .parameters(tool.getParametersSchema())
                        .build())
                .build()).collect(Collectors.toList());
    }

    public AgentTool getTool(String name) {
        return tools.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
    }
}
