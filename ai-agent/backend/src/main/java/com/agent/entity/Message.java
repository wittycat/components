package com.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_calls", columnDefinition = "JSON")
    private String toolCalls;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
