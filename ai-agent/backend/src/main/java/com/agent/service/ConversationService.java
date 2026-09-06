package com.agent.service;

import com.agent.config.AgentConfig;
import com.agent.dto.ChatMessageDto;
import com.agent.entity.Conversation;
import com.agent.entity.Message;
import com.agent.repository.ConversationRepository;
import com.agent.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;

    public List<Conversation> listConversations() {
        log.info("[Conversation] 查询对话列表");
        List<Conversation> conversations = conversationRepository.findAllByOrderByUpdatedAtDesc();
        log.info("[Conversation] 查询到 {} 个对话", conversations.size());
        return conversations;
    }

    @Transactional
    public Conversation createConversation(String title) {
        log.info("[Conversation] 创建对话, title={}", title);
        Conversation conv = new Conversation();
        conv.setTitle(title != null ? title : "新对话");
        Conversation saved = conversationRepository.save(conv);
        log.info("[Conversation] 对话创建成功, id={}", saved.getId());
        return saved;
    }

    public Conversation getConversation(Long id) {
        log.info("[Conversation] 查询对话, id={}", id);
        return conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在: " + id));
    }

    @Transactional
    public void deleteConversation(Long id) {
        log.info("[Conversation] 删除对话, id={}", id);
        conversationRepository.deleteById(id);
        log.info("[Conversation] 对话删除成功, id={}", id);
    }

    public List<Message> getMessages(Long conversationId) {
        log.info("[Conversation] 查询消息, conversationId={}", conversationId);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        log.info("[Conversation] 查询到 {} 条消息", messages.size());
        return messages;
    }

    /**
     * 加载对话记忆：返回最近 N 条消息，转换为 LLM 消息格式
     */
    public List<ChatMessageDto> loadMemory(Long conversationId) {
        List<Message> allMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int maxMessages = agentConfig.getMemoryMaxMessages();

        List<Message> recent;
        if (allMessages.size() <= maxMessages) {
            recent = allMessages;
        } else {
            recent = allMessages.subList(allMessages.size() - maxMessages, allMessages.size());
        }

        List<ChatMessageDto> result = new ArrayList<>();
        for (Message msg : recent) {
            ChatMessageDto.ChatMessageDtoBuilder builder = ChatMessageDto.builder()
                    .role(msg.getRole())
                    .content(msg.getContent());

            if (msg.getToolCallId() != null) {
                builder.toolCallId(msg.getToolCallId());
            }
            if (msg.getToolCalls() != null) {
                try {
                    List<ChatMessageDto.ToolCallDto> toolCalls = objectMapper.readValue(
                            msg.getToolCalls(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ChatMessageDto.ToolCallDto.class));
                    builder.toolCalls(toolCalls);
                } catch (Exception e) {
                    log.warn("[Conversation] toolCalls JSON 反序列化失败, msgId={}: {}",
                            msg.getId(), e.getMessage());
                }
            }
            result.add(builder.build());
        }
        return result;
    }

    @Transactional
    public Message saveMessage(Long conversationId, String role, String content) {
        return saveMessage(conversationId, role, content, null, null);
    }

    @Transactional
    public Message saveMessage(Long conversationId, String role, String content,
                               String toolCalls, String toolCallId) {
        log.info("[Conversation] 保存消息, conversationId={}, role={}, content长度={}", 
                conversationId, role, content != null ? content.length() : 0);
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        msg.setToolCallId(toolCallId);
        Message saved = messageRepository.save(msg);
        log.info("[Conversation] 消息保存成功, id={}", saved.getId());
        return saved;
    }

    @Transactional
    public void updateTitle(Long conversationId, String firstMessage) {
        log.info("[Conversation] 更新对话标题, conversationId={}, firstMessage长度={}", 
                conversationId, firstMessage != null ? firstMessage.length() : 0);
        Conversation conv = getConversation(conversationId);
        if ("新对话".equals(conv.getTitle()) && firstMessage != null) {
            String newTitle = firstMessage.length() > 30 ? firstMessage.substring(0, 30) + "..." : firstMessage;
            conv.setTitle(newTitle);
            conversationRepository.save(conv);
            log.info("[Conversation] 标题已更新: {}", newTitle);
        }
    }
}
