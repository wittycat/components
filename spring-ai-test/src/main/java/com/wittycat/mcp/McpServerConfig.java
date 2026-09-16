package com.wittycat.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 装配:把 @Tool 方法注册进 MCP Server。
 *
 * 【知识点】
 * 1. starter 自动配置会检测容器里的 ToolCallbackProvider Bean,把它包装的工具全部
 *    注册到 MCP Server——业务代码只管提供工具对象,完全不碰 MCP 协议细节。
 * 2. 和示例 4 的 @Tool 是同一个注解、两种出口:那里 tools(...) 喂给 ChatClient
 *    (模型自己调用);这里喂给 MCP Server(外部 AI 应用跨进程调用)。
 *
 * @author  javachenxun
 * @date 2026/09/16
 */
@Configuration
public class McpServerConfig {

    /**
     * 把 McpTestTools 里的 @Tool 方法逐个包装成 MCP tool。
     *
     * @param tools 工具对象,方法上的 @Tool/@ToolParam 描述会原样进 MCP 协议
     * @return 工具提供器,starter 自动配置检测到即注册进 MCP Server
     */
    @Bean
    public ToolCallbackProvider mcpTools(McpTestTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
