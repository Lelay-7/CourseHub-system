package com.bugaoshan.chat;
import com.bugaoshan.common.ChatException;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
class ChatConversationTest {
    ChatTestFixture f;
    @BeforeEach void setup() throws Exception { f=new ChatTestFixture(); f.seedUsers(); f.friend(1,2); }
    long open() { var result=f.conversations.openPrivate(f.actor(1,0),2); assertNotNull(result,"private conversation must be persisted and returned"); return Long.parseLong(result.conversationId()); }
    @Test void privateConversationIsReused() {
        long id=open(); assertEquals(""+id,f.conversations.openPrivate(f.actor(2,0),1).conversationId());
        assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_conversation",Integer.class));
        assertEquals(2,f.db.queryForObject("SELECT COUNT(*) FROM chat_conversation_member",Integer.class));
    }
    @Test void outsiderAndAdminCannotRead() {
        long id=open(); assertEquals(404,assertThrows(ChatException.class,()->f.conversations.detail(f.actor(3,0),id)).status());
        assertEquals(404,assertThrows(ChatException.class,()->f.conversations.detail(f.actor(3,0),999)).status());
    }
    @Test void unfriendingKeepsReadOnlyHistory() {
        long id=open(); f.db.update("DELETE FROM friend");
        assertFalse(f.conversations.detail(f.actor(1,0),id).canSend());
        assertEquals("NOT_FRIENDS",f.conversations.detail(f.actor(1,0),id).sendDisabledReason());
        assertEquals(403,assertThrows(ChatException.class,()->f.conversations.openPrivate(f.actor(1,0),2)).status());
        f.friend(2,1); assertEquals(""+id,f.conversations.openPrivate(f.actor(1,0),2).conversationId());
        f.db.update("UPDATE user SET is_deleted=1 WHERE user_id=2");
        assertEquals("账号不可用",f.conversations.detail(f.actor(1,0),id).peer().nickname());
    }
    @Test void readCursorCannotRegressOrCrossConversations() {
        long id=open(); f.seedMessage(id,1,2,"一"); f.seedMessage(id,2,1,"二");
        f.friend(1,3);long other=Long.parseLong(f.conversations.openPrivate(f.actor(1,0),3).conversationId());f.seedMessage(other,3,3,"其他会话");
        assertEquals(2,f.conversations.unread(f.actor(1,0)).unreadCount());
        assertEquals("2",f.conversations.markRead(f.actor(1,0),id,2).lastReadSequence());
        assertEquals("2",f.conversations.markRead(f.actor(1,0),id,1).lastReadSequence());
        assertEquals(400,assertThrows(ChatException.class,()->f.conversations.markRead(f.actor(1,0),id,3)).status());
        assertEquals(0,f.conversations.detail(f.actor(1,0),id).unreadCount());
        assertEquals("0",f.conversations.detail(f.actor(1,0),other).lastReadSequence());
        assertEquals(1,f.conversations.unread(f.actor(1,0)).unreadCount());
    }
    @Test void peerJsonContainsNoPrivateFieldsAndLargeIdsRemainStrings() throws Exception {
        long id=open(); var json=new ObjectMapper().findAndRegisterModules().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).valueToTree(f.conversations.detail(f.actor(1,0),id));
        assertTrue(json.get("conversation_id").isTextual());
        assertEquals(5,json.get("peer").size());
        assertFalse(json.toString().contains("private@example")); assertFalse(json.toString().contains("hidden"));
        f.db.update("UPDATE chat_conversation SET last_sequence=9007199254740993 WHERE conversation_id=?",id);
        assertEquals("9007199254740993",f.conversations.detail(f.actor(1,0),id).lastSequence());
    }
    @Test void staleCredentialCannotCreateOrMarkRead() {
        long id=open(); f.seedMessage(id,1,2,"一"); f.db.update("UPDATE user SET credential_version=1 WHERE user_id=1");
        assertEquals(401,assertThrows(ChatException.class,()->f.conversations.openPrivate(f.actor(1,0),2)).status());
        assertEquals(401,assertThrows(ChatException.class,()->f.conversations.markRead(f.actor(1,0),id,1)).status());
    }
}
