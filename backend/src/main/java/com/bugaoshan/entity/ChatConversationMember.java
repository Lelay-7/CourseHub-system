package com.bugaoshan.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ChatConversationMember {
    private Long conversationId,userId,lastReadSequence;
    private LocalDateTime joinedAt,updatedAt;
}
