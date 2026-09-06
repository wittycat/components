package com.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 是否启用 RAG 检索 */
    private boolean enableRag = true;

    /** 是否启用工具调用 */
    private boolean enableTools = true;
}
