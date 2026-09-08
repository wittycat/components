package com.wittycat.knowledgebase.tool;

import com.wittycat.knowledgebase.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public String execute(String toolName, String argumentsJson) {
        try {
            AgentTool tool = toolRegistry.getTool(toolName);
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argumentsJson, Map.class);
            log.info("Executing tool: {} with args: {}", toolName, args);
            return tool.execute(args);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }
}
