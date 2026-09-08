package com.wittycat.knowledgebase.tool;

import java.util.Map;

/**
 * Agent 工具接口：所有可被 LLM 调用的工具需实现此接口
 */
public interface AgentTool {

    /** 工具名称（与 LLM function name 对应） */
    String getName();

    /** 工具描述 */
    String getDescription();

    /** JSON Schema 格式的参数定义 */
    Map<String, Object> getParametersSchema();

    /** 执行工具，返回结果字符串 */
    String execute(Map<String, Object> arguments) throws Exception;
}
