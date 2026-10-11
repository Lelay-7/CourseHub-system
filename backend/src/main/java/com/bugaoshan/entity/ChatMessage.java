package com.bugaoshan.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ChatMessage {
    private Long messageId,conversationId,sequence,senderId;
    private String clientMessageId,messageType,content;
    private LocalDateTime createdAt;
}
