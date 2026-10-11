package com.bugaoshan.chat;
import com.bugaoshan.dto.ChatDTO.*;
import com.bugaoshan.common.ChatException;
import com.bugaoshan.service.ChatMessageService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChatMessageTest {
    ChatTestFixture f; ChatMessageService messages; long id;
    @BeforeEach void setup() throws Exception { f=new ChatTestFixture(); f.seedUsers(); f.friend(1,2); id=Long.parseLong(f.conversations.openPrivate(f.actor(1,0),2).conversationId()); messages=f.messages; }
    SendTextRequest text(String value) { return new SendTextRequest(UUID.randomUUID(),"TEXT",value); }
    @Test void responseAndRetryUseTheSameDatabaseMillisecondTimestamp() {
        var precise=java.time.Clock.fixed(java.time.Instant.parse("2026-10-11T10:00:00.123456Z"),java.time.ZoneOffset.UTC);
        var service=new ChatMessageService(f.conversations,f.relationships,f.session.getMapper(com.bugaoshan.mapper.ChatConversationMapper.class),f.session.getMapper(com.bugaoshan.mapper.ChatMessageMapper.class),f.rates,f.manager,e->{},precise);
        var request=text("timestamp");var first=service.send(f.actor(1,0),id,request);
        assertEquals(java.time.Instant.parse("2026-10-11T10:00:00.123Z"),first.createdAt());
        assertEquals(first,service.send(f.actor(1,0),id,request));
    }
    @Test void lostResponseRetryPersistsOnceAndConsumesOneQuota() {
        var request=text("😀".repeat(2000)); var first=messages.send(f.actor(1,0),id,request);
        assertNotNull(first,"send must persist and return a message");
        assertEquals(first.messageId(),messages.send(f.actor(1,0),id,request).messageId());
        assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));
        assertEquals(1,f.events.size());
        assertEquals(1,f.db.queryForObject("SELECT used_count FROM chat_send_rate_window WHERE user_id=1 AND window_seconds=60",Integer.class));
        assertEquals(409,assertThrows(ChatException.class,()->messages.send(f.actor(1,0),id,new SendTextRequest(request.clientMessageId(),"TEXT","changed"))).status());
    }
    @Test void unicodeBoundariesAndInvalidTextAreRejected() {
        assertEquals("a\nb\nc",messages.normalizeText("a\r\nb\rc"));
        for(String value:List.of("", "\u00a0\u3000\t\n", "😀".repeat(2001),"a\u0000b","a\u007fb","\ud800"))
            assertEquals(400,assertThrows(ChatException.class,()->messages.send(f.actor(1,0),id,text(value))).status());
        assertEquals(400,assertThrows(ChatException.class,()->messages.send(f.actor(1,0),id,new SendTextRequest(UUID.randomUUID(),"HTML","hello"))).status());
        assertEquals(400,assertThrows(ChatException.class,()->messages.send(f.actor(1,0),id,new SendTextRequest(null,"TEXT","hello"))).status());
    }
    @Test void permissionsPrecedeIdempotencyAndCredentialVersionIsRechecked() {
        var request=text("one"); assertNotNull(messages.send(f.actor(1,0),id,request)); f.db.update("DELETE FROM friend");
        assertEquals(403,assertThrows(ChatException.class,()->messages.send(f.actor(1,0),id,request)).status());
        assertNotNull(messages.messages(f.actor(1,0),id,null,null,30)); f.friend(1,2);
        f.db.update("UPDATE user SET credential_version=1 WHERE user_id=1");
        assertEquals(401,assertThrows(ChatException.class,()->messages.send(f.actor(1,0),id,text("stale"))).status());
        assertEquals(404,assertThrows(ChatException.class,()->messages.messages(f.actor(3,0),id,null,null,30)).status());
    }
    @Test void incrementalPagesDoNotSkipMessagesAndHistoryIsAscending() {
        for(int i=1;i<=7;i++) assertNotNull(messages.send(f.actor(1,0),id,text("line "+i)));
        var page=messages.messages(f.actor(2,0),id,null,0L,3); assertNotNull(page);
        assertEquals(List.of("1","2","3"),page.items().stream().map(MessageView::sequence).toList()); assertTrue(page.hasMore()); assertEquals("3",page.nextAfterSequence());
        var next=messages.messages(f.actor(2,0),id,null,3L,3); assertEquals(List.of("4","5","6"),next.items().stream().map(MessageView::sequence).toList());
        var latest=messages.messages(f.actor(2,0),id,null,null,3); assertEquals(List.of("5","6","7"),latest.items().stream().map(MessageView::sequence).toList());
        assertEquals(400,assertThrows(ChatException.class,()->messages.messages(f.actor(1,0),id,1L,1L,30)).status());
    }
    @Test void failedInsertRollsBackMessageSequenceQuotaAndEvent() {
        f.db.execute("ALTER TABLE chat_message ADD CONSTRAINT reject_content CHECK(content NOT LIKE '%reject%')");
        assertThrows(RuntimeException.class,()->messages.send(f.actor(1,0),id,text("reject")));
        assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));
        assertEquals(0,f.db.queryForObject("SELECT last_sequence FROM chat_conversation WHERE conversation_id=?",Long.class,id));
        assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM chat_send_rate_window",Integer.class));
        assertTrue(f.events.isEmpty());
    }
}
