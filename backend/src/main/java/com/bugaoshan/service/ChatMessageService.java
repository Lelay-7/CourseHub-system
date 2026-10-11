package com.bugaoshan.service;
import com.bugaoshan.dto.ChatDTO.*;
import com.bugaoshan.entity.ChatMessage;
import com.bugaoshan.mapper.*;
import com.bugaoshan.common.ChatException;
import com.bugaoshan.chat.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
@Service
public class ChatMessageService {
    private final ChatConversationService conversations;
    private final FriendRelationshipService relationships;
    private final ChatConversationMapper chats;
    private final ChatMessageMapper mapper;
    private final ChatSendRateLimiter rates;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate tx;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ChatMessageService.class);
    public ChatMessageService(ChatConversationService conversations,FriendRelationshipService relationships,ChatConversationMapper chats,ChatMessageMapper mapper,ChatSendRateLimiter rates,PlatformTransactionManager manager,ApplicationEventPublisher publisher,@Qualifier("chatClock") Clock clock) {
        this.conversations=conversations; this.relationships=relationships; this.chats=chats; this.mapper=mapper; this.rates=rates; this.publisher=publisher; this.clock=clock;
        tx=new TransactionTemplate(manager); tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public MessageView send(Actor actor,long conversationId,SendTextRequest request) {
        if(request==null || request.clientMessageId()==null || !"TEXT".equals(request.messageType())) throw new ChatException(400,"消息类型或客户端消息 ID 不正确");
        String normalized=normalizeText(request.text());
        return tx.execute(status->{
            var locked=conversations.lockMembersForWrite(actor,conversationId);
            long peer=locked.lowerId()==actor.userId()?locked.higherId():locked.lowerId();
            if(!ChatConversationService.active(locked.user(peer))) throw new ChatException(403,"对方账号不可用");
            if(!relationships.isAcceptedForWrite(actor.userId(),peer)) throw new ChatException(403,"解除好友后不能发送消息");
            var conversation=chats.selectForUpdate(conversationId);
            String clientId=request.clientMessageId().toString();
            ChatMessage previous=mapper.idempotent(actor.userId(),clientId);
            if(previous!=null) {
                MessageView view=ChatMessageViews.view(previous,2000);
                if(previous.getConversationId()!=conversationId || !"TEXT".equals(previous.getMessageType()) || !Objects.equals(normalized,view.content().text())) throw new ChatException(409,"客户端消息 ID 已用于另一条消息");
                return view;
            }
            Instant instant=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); rates.reserve(actor.userId(),instant);
            long sequence=Math.addExact(conversation.getLastSequence(),1);
            var now=LocalDateTime.ofInstant(instant,ZoneOffset.UTC);
            ChatMessage row=new ChatMessage(); row.setConversationId(conversationId); row.setSequence(sequence); row.setSenderId(actor.userId()); row.setClientMessageId(clientId); row.setMessageType("TEXT"); row.setCreatedAt(now);
            try { row.setContent(JSON.writeValueAsString(new TextContent(normalized))); } catch(Exception e) { throw new IllegalStateException("TEXT serialization failed",e); }
            mapper.insert(row); mapper.advance(conversationId,sequence,now);
            var event=new ChatMessageCreated(conversationId,row.getMessageId(),sequence,List.of(locked.lowerId(),locked.higherId()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { publisher.publishEvent(event); } catch(Exception e) { log.warn("Chat commit notification failed for message {}",row.getMessageId(),e); }
                }
            });
            return ChatMessageViews.view(row,2000);
        });
    }
    public MessagePage messages(Actor actor,long conversationId,Long before,Long after,int limit) {
        ChatConversationService.validateLimit(limit);
        if((before!=null && after!=null) || (before!=null && before<=0) || (after!=null && after<0)) throw new ChatException(400,"分页游标不正确");
        var conversation=conversations.requireMember(actor,conversationId);
        var rows=new ArrayList<>(mapper.page(conversationId,before,after,limit+1)); boolean more=rows.size()>limit;
        if(more) rows.removeLast();
        if(after==null) Collections.reverse(rows);
        var items=rows.stream().map(row->ChatMessageViews.view(row,2000)).toList();
        String first=items.isEmpty()?null:items.getFirst().sequence();
        String last=items.isEmpty()?(after==null?"0":""+after):items.getLast().sequence();
        long latest=Math.max(conversation.getLastSequence(),Long.parseLong(last));
        return new MessagePage(items,first,last,more,""+latest);
    }
    public String normalizeText(String text) {
        if(text==null) throw new ChatException(400,"请输入消息");
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)) { if(i+1>=text.length() || !Character.isLowSurrogate(text.charAt(++i))) throw new ChatException(400,"消息包含无效字符"); }
            else if(Character.isLowSurrogate(c)) throw new ChatException(400,"消息包含无效字符");
        }
        String normalized=text.replace("\r\n","\n").replace('\r','\n');
        int count=normalized.codePointCount(0,normalized.length());
        if(count<1 || count>2000) throw new ChatException(400,"消息应为 1–2000 个字符");
        boolean blank=true;
        for(int cp:normalized.codePoints().toArray()) {
            if(Character.isISOControl(cp) && cp!='\n' && cp!='\t') throw new ChatException(400,"消息包含不支持的控制字符");
            if(!(Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp==0x85)) blank=false;
        }
        if(blank) throw new ChatException(400,"消息不能只有空白");
        return normalized;
    }
}
