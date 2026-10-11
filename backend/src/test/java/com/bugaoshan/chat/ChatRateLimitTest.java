package com.bugaoshan.chat;
import com.bugaoshan.service.ChatMessageService;
import com.bugaoshan.common.ChatException;
import com.bugaoshan.dto.ChatDTO.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ChatRateLimitTest {
    @Test void hourQuotaFailureRollsBackMinuteReservation() throws Exception {
        var f=new ChatTestFixture(); f.seedUsers(); f.friend(1,2); long id=Long.parseLong(f.conversations.openPrivate(f.actor(1,0),2).conversationId());
        f.messages.send(f.actor(1,0),id,new SendTextRequest(UUID.randomUUID(),"TEXT","one"));
        f.db.update("UPDATE chat_send_rate_window SET used_count=300 WHERE window_seconds=3600");
        assertEquals(429,assertThrows(ChatException.class,()->f.messages.send(f.actor(1,0),id,new SendTextRequest(UUID.randomUUID(),"TEXT","blocked"))).status());
        assertEquals(1,f.db.queryForObject("SELECT used_count FROM chat_send_rate_window WHERE window_seconds=60",Integer.class));
        assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));
    }
    @Test void minuteLimitRejectsThirtyFirstNewMessageButAllowsSameIdRetry() throws Exception {
        var f=new ChatTestFixture(); f.seedUsers(); f.friend(1,2); long id=Long.parseLong(f.conversations.openPrivate(f.actor(1,0),2).conversationId());
        var service=f.messages; var first=new SendTextRequest(UUID.randomUUID(),"TEXT","first");
        assertNotNull(service.send(f.actor(1,0),id,first));
        for(int i=1;i<30;i++) service.send(f.actor(1,0),id,new SendTextRequest(UUID.randomUUID(),"TEXT","line"));
        var error=assertThrows(ChatException.class,()->service.send(f.actor(1,0),id,new SendTextRequest(UUID.randomUUID(),"TEXT","over")));
        assertEquals(429,error.status()); assertTrue(error.retryAfterSeconds()>0);
        assertNotNull(service.send(f.actor(1,0),id,first));
        assertEquals(30,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));
    }
}
