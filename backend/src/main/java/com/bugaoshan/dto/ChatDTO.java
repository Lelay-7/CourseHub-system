package com.bugaoshan.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class ChatDTO {
    private ChatDTO() {}
    public record Actor(long userId,int credentialVersion) {}
    public record Peer(String userId,String nickname,String avatarUrl,Integer userType,String department) {}
    public record TextContent(String text) {}
    public record MessageView(String messageId,String conversationId,String sequence,String senderId,
        String clientMessageId,String messageType,TextContent content,Instant createdAt) {}
    public record ConversationView(String conversationId,String conversationType,Peer peer,MessageView lastMessage,
        String lastSequence,String lastReadSequence,long unreadCount,boolean canSend,String sendDisabledReason) {}
    public record ConversationPage(List<ConversationView> items,String nextCursor) {}
    public record MessagePage(List<MessageView> items,String nextBeforeSequence,String nextAfterSequence,boolean hasMore,String latestSequence) {}
    public record ReadView(String lastReadSequence,long unreadCount) {}
    public record UnreadView(long unreadCount) {}
    public record CreatePrivateRequest(Long targetUserId) {}
    public record SendTextRequest(UUID clientMessageId,String messageType,String text) {}
    public record ReadRequest(String lastReadSequence) {}
}
