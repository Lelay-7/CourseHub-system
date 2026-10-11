package com.bugaoshan.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ChatConversation {
    private Long conversationId;
    private String conversationType;
    private Long privateUserLowId,privateUserHighId,lastSequence;
    private LocalDateTime lastMessageAt,createdAt,updatedAt;
}
