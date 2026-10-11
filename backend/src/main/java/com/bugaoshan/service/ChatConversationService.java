package com.bugaoshan.service;
import com.bugaoshan.dto.ChatDTO.*;
import com.bugaoshan.entity.*;
import com.bugaoshan.mapper.*;
import com.bugaoshan.common.ChatException;
import com.bugaoshan.chat.ChatMessageViews;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public class ChatConversationService {
    private final UserMapper users;
    private final FriendRelationshipService relationships;
    private final ChatConversationMapper chats;
    private final TransactionTemplate tx;
    private final Clock clock;
    @Autowired public ChatConversationService(UserMapper users,FriendRelationshipService relationships,ChatConversationMapper chats,PlatformTransactionManager manager) { this(users,relationships,chats,manager,Clock.systemUTC()); }
    public ChatConversationService(UserMapper users,FriendRelationshipService relationships,ChatConversationMapper chats,PlatformTransactionManager manager,Clock clock) {
        this.users=users; this.relationships=relationships; this.chats=chats; this.clock=clock;
        tx=new TransactionTemplate(manager); tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public ConversationView openPrivate(Actor actor,long targetId) {
        if(targetId<=0 || actor.userId()==targetId) throw new ChatException(400,"请选择其他用户");
        return tx.execute(status->{
            var locked=relationships.lockUsers(actor.userId(),targetId); validateActor(actor,locked.user(actor.userId()));
            if(!active(locked.user(targetId))) throw new ChatException(404,"账号不可用");
            if(!relationships.isAcceptedForWrite(actor.userId(),targetId)) throw new ChatException(403,"只有好友可以开始聊天");
            ChatConversation row=chats.selectPair(locked.lowerId(),locked.higherId());
            if(row==null) {
                row=new ChatConversation(); row.setPrivateUserLowId(locked.lowerId()); row.setPrivateUserHighId(locked.higherId());
                row.setConversationType("PRIVATE"); row.setLastSequence(0L); row.setCreatedAt(now()); row.setUpdatedAt(row.getCreatedAt());
                chats.insert(row); chats.insertMember(row.getConversationId(),locked.lowerId(),row.getCreatedAt()); chats.insertMember(row.getConversationId(),locked.higherId(),row.getCreatedAt());
            }
            return view(actor,row);
        });
    }
    public ConversationPage list(Actor actor,int limit,String cursor) {
        validateLimit(limit); LocalDateTime time=null; Long id=null;
        if(cursor!=null) try {
            if(cursor.isBlank() || cursor.length()>160) throw new IllegalArgumentException();
            String[] parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);
            if(parts.length!=2) throw new IllegalArgumentException(); time=LocalDateTime.parse(parts[0]); id=parseSequence(parts[1],false);
        } catch(Exception e) { throw new ChatException(400,"会话游标不正确"); }
        List<ChatConversation> rows=chats.list(actor.userId(),limit+1,time,id); boolean more=rows.size()>limit;
        if(more) rows=new ArrayList<>(rows.subList(0,limit));
        String next=null;
        if(more) { var last=rows.getLast(); var activity=last.getLastMessageAt()==null?last.getCreatedAt():last.getLastMessageAt(); next=Base64.getUrlEncoder().withoutPadding().encodeToString((activity+"|"+last.getConversationId()).getBytes(StandardCharsets.UTF_8)); }
        return new ConversationPage(rows.stream().map(row->view(actor,row)).toList(),next);
    }
    public ConversationView detail(Actor actor,long id) { return view(actor,requireMember(actor,id)); }
    public ChatConversation requireMember(Actor actor,long id) {
        ChatConversation row=chats.selectForMember(id,actor.userId());
        if(row==null) throw new ChatException(404,"会话不可访问"); return row;
    }
    public ReadView markRead(Actor actor,long id,long sequence) {
        if(sequence<0) throw new ChatException(400,"消息序号不正确");
        return tx.execute(status->{
            lockMembersForWrite(actor,id); chats.selectForUpdate(id);
            if(sequence!=0 && chats.messageExists(id,sequence)!=1) throw new ChatException(400,"消息序号不属于本会话");
            chats.markRead(id,actor.userId(),sequence,now());
            return new ReadView(""+chats.member(id,actor.userId()).getLastReadSequence(),chats.unread(id,actor.userId()));
        });
    }
    public UnreadView unread(Actor actor) { return new UnreadView(chats.totalUnread(actor.userId())); }
    public FriendRelationshipService.LockedUsers lockMembersForWrite(Actor actor,long id) {
        ChatConversation row=requireMember(actor,id);
        var locked=relationships.lockUsers(row.getPrivateUserLowId(),row.getPrivateUserHighId());
        validateActor(actor,locked.user(actor.userId())); return locked;
    }
    public void validateActor(Actor actor,User user) {
        if(!active(user) || !Objects.equals(user.getCredentialVersion(),actor.credentialVersion())) throw new ChatException(401,"登录已失效，请重新登录");
    }
    public static boolean active(User user) { return user!=null && Integer.valueOf(1).equals(user.getStatus()) && !Integer.valueOf(1).equals(user.getIsDeleted()); }
    public static void validateLimit(int limit) { if(limit<1 || limit>100) throw new ChatException(400,"每页数量应为 1–100"); }
    public static long parseSequence(String value,boolean allowZero) {
        try { if(value==null || !value.matches("[0-9]{1,19}")) throw new IllegalArgumentException(); long n=Long.parseLong(value); if(n<0 || (!allowZero && n==0)) throw new IllegalArgumentException(); return n; }
        catch(Exception e) { throw new ChatException(400,"消息或会话序号不正确"); }
    }
    private ConversationView view(Actor actor,ChatConversation row) {
        long peerId=row.getPrivateUserLowId()==actor.userId()?row.getPrivateUserHighId():row.getPrivateUserLowId();
        User user=users.selectById(peerId); boolean available=active(user); boolean accepted=relationships.isAccepted(actor.userId(),peerId);
        Peer peer=available?new Peer(""+peerId,user.getNickname()==null || user.getNickname().isBlank()?user.getUsername():user.getNickname(),user.getAvatarUrl(),user.getUserType(),user.getDepartment()):new Peer(""+peerId,"账号不可用",null,null,null);
        var member=chats.member(row.getConversationId(),actor.userId());
        return new ConversationView(""+row.getConversationId(),row.getConversationType(),peer,ChatMessageViews.view(chats.lastMessage(row.getConversationId()),80),""+row.getLastSequence(),""+member.getLastReadSequence(),chats.unread(row.getConversationId(),actor.userId()),available && accepted,available?(accepted?null:"NOT_FRIENDS"):"PEER_UNAVAILABLE");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),ZoneOffset.UTC); }
}
