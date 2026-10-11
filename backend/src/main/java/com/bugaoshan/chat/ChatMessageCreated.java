package com.bugaoshan.chat;
import java.util.List;
/** Commit notification only. Future external delivery requires an independent outbox. */
public record ChatMessageCreated(long conversationId,long messageId,long sequence,List<Long> participantIds) {
    public ChatMessageCreated { participantIds=List.copyOf(participantIds); }
}
