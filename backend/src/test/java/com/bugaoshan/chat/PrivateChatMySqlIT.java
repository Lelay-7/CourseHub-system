package com.bugaoshan.chat;
import com.bugaoshan.common.ChatException;
import com.bugaoshan.dto.ChatDTO.*;
import com.bugaoshan.mapper.*;
import com.bugaoshan.service.ChatMessageService;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in real MySQL suite: refuses existing/non-loopback databases before any mutation. */
class PrivateChatMySqlIT {
    ChatTestFixture f;
    @BeforeEach void setup() throws Exception {
        f=new ChatTestFixture(PrivateChatE2eApplication.guardedSource(),false);
        for(String table:List.of("chat_message","chat_conversation_member","chat_conversation","chat_send_rate_window","friend","contact_auth","recovery_code","recovery_rate_event","recovery_rate_lock","user"))f.db.update("DELETE FROM "+table);
        f.seedUsers();
    }
    ChatTestFixture instance() throws Exception { return new ChatTestFixture(PrivateChatE2eApplication.guardedSource(),false); }
    long open(){f.friend(1,2);return Long.parseLong(f.conversations.openPrivate(f.actor(1,0),2).conversationId());}
    SendTextRequest text(String s){return new SendTextRequest(UUID.randomUUID(),"TEXT",s);}
    <T> List<T> concurrent(Callable<T> a,Callable<T> b) throws Exception {
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{start.await();return a.call();});var second=pool.submit(()->{start.await();return b.call();});start.countDown();
            return List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
        }
    }
    void waitForLock() {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(f.db.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)==0 && System.nanoTime()<deadline)Thread.onSpinWait();
        assertTrue(f.db.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)>0,"Sender must actually be waiting on the held user lock");
    }
    @Test void concurrentCreatesReuseOneConversation() throws Exception {
        f.friend(1,2);var other=instance();
        var results=concurrent(()->f.conversations.openPrivate(f.actor(1,0),2),()->other.conversations.openPrivate(f.actor(2,0),1));
        assertEquals(results.get(0).conversationId(),results.get(1).conversationId());
        assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_conversation",Integer.class));assertEquals(2,f.db.queryForObject("SELECT COUNT(*) FROM chat_conversation_member",Integer.class));
    }
    @Test void oppositeApplicationsProduceOneAcceptedPairAndRepeatAcceptIsRejected() throws Exception {
        var other=instance();concurrent(()->f.relationships.addFriend(1,2),()->other.relationships.addFriend(2,1));
        assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM friend",Integer.class));assertEquals(1,f.db.queryForObject("SELECT status FROM friend",Integer.class));
        long friend=f.db.queryForObject("SELECT friend_id FROM friend",Long.class);
        assertThrows(com.bugaoshan.common.BusinessException.class,()->f.relationships.accept(2,friend));
    }
    @Test void concurrentSameIdPersistsOnceAndUsesOneQuota() throws Exception {
        long id=open();var other=instance();var request=text("response lost");
        var rows=concurrent(()->f.messages.send(f.actor(1,0),id,request),()->other.messages.send(f.actor(1,0),id,request));
        assertEquals(rows.get(0).messageId(),rows.get(1).messageId());
        assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));assertEquals(1,f.db.queryForObject("SELECT used_count FROM chat_send_rate_window WHERE window_seconds=60",Integer.class));
        f.friend(1,3);long second=Long.parseLong(f.conversations.openPrivate(f.actor(1,0),3).conversationId());
        assertEquals(409,assertThrows(ChatException.class,()->other.messages.send(f.actor(1,0),second,request)).status());
    }
    @Test void concurrentAcceptAndRemoveNeverResurrectsTheRelationship() throws Exception {
        f.relationships.addFriend(1,2);long friend=f.db.queryForObject("SELECT friend_id FROM friend",Long.class);var other=instance();
        var results=concurrent(()->{f.relationships.remove(1,friend);return true;},()->{try{other.relationships.accept(2,friend);return true;}catch(com.bugaoshan.common.BusinessException e){return false;}});
        assertTrue(results.getFirst());assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM friend",Integer.class));
    }
    @Test void aCommittedSendRemainsValidWhenUnfriendWasWaiting() throws Exception {
        long id=open(),friend=f.db.queryForObject("SELECT friend_id FROM friend",Long.class);var other=instance();
        var inserted=new CountDownLatch(1);var release=new CountDownLatch(1);var real=f.session.getMapper(ChatMessageMapper.class);
        ChatMessageMapper paused=(ChatMessageMapper)Proxy.newProxyInstance(ChatMessageMapper.class.getClassLoader(),new Class[]{ChatMessageMapper.class},(proxy,method,args)->{
            Object value;try{value=method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
            if(method.getName().equals("insert")){inserted.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Commit barrier timed out");}return value;
        });
        var slow=new ChatMessageService(f.conversations,f.relationships,f.session.getMapper(ChatConversationMapper.class),paused,f.rates,f.manager,e->{},f.clock);
        try(var pool=Executors.newFixedThreadPool(2)){
            var sending=pool.submit(()->slow.send(f.actor(1,0),id,text("committed")));assertTrue(inserted.await(5,TimeUnit.SECONDS));
            var removing=pool.submit(()->{other.relationships.remove(2,friend);return true;});waitForLock();release.countDown();
            assertEquals("1",sending.get(10,TimeUnit.SECONDS).sequence());assertTrue(removing.get(10,TimeUnit.SECONDS));
            assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));assertFalse(f.conversations.detail(f.actor(1,0),id).canSend());
        }finally{release.countDown();}
    }
    @Test void sendAndUnfriendHaveAValidSerialization() throws Exception {
        long id=open(),friend=f.db.queryForObject("SELECT friend_id FROM friend",Long.class);var sender=instance();
        try(var pool=Executors.newSingleThreadExecutor()) {
            var result=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            new TransactionTemplate(f.manager).executeWithoutResult(tx->{
                f.users.selectForUpdate(1L);
                result.set(pool.submit(()->{try{sender.messages.send(f.actor(1,0),id,text("blocked"));return 200;}catch(ChatException e){return e.status();}}));
                waitForLock();f.relationships.remove(2,friend);
            });
            assertEquals(403,result.get().get(10,TimeUnit.SECONDS));
        }
        assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));assertFalse(f.conversations.detail(f.actor(1,0),id).canSend());
        f.friend(1,2);assertEquals(""+id,f.conversations.openPrivate(f.actor(1,0),2).conversationId());
    }
    @Test void passwordChangeRejectsAnOldWaitingSender() throws Exception {
        long id=open();var sender=instance();try(var pool=Executors.newSingleThreadExecutor()){
            var result=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            new TransactionTemplate(f.manager).executeWithoutResult(tx->{
                f.users.selectForUpdate(1L);
                result.set(pool.submit(()->{try{sender.messages.send(f.actor(1,0),id,text("stale"));return 200;}catch(ChatException e){return e.status();}}));
                waitForLock();assertEquals(1,f.users.changePassword(1L,"new-hash",0));
            });assertEquals(401,result.get().get(10,TimeUnit.SECONDS));
        }
        assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM chat_send_rate_window",Integer.class));
    }
    @Test void assignedSequenceCannotBeSkippedBeforeEarlierCommit() throws Exception {
        long id=open();var other=instance();var inserted=new CountDownLatch(1);var release=new CountDownLatch(1);
        var real=f.session.getMapper(ChatMessageMapper.class);
        ChatMessageMapper paused=(ChatMessageMapper)Proxy.newProxyInstance(ChatMessageMapper.class.getClassLoader(),new Class[]{ChatMessageMapper.class},(proxy,method,args)->{
            Object value;try{value=method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
            if(method.getName().equals("insert")){inserted.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Commit barrier timed out");}return value;
        });
        var slow=new ChatMessageService(f.conversations,f.relationships,f.session.getMapper(ChatConversationMapper.class),paused,f.rates,f.manager,e->f.events.add((ChatMessageCreated)e),f.clock);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->slow.send(f.actor(1,0),id,text("first")));assertTrue(inserted.await(5,TimeUnit.SECONDS));
            var second=pool.submit(()->other.messages.send(f.actor(2,0),id,text("second")));waitForLock();
            assertTrue(other.messages.messages(f.actor(2,0),id,null,0L,30).items().isEmpty());
            release.countDown();assertEquals("1",first.get(10,TimeUnit.SECONDS).sequence());assertEquals("2",second.get(10,TimeUnit.SECONDS).sequence());
            assertEquals(List.of("1","2"),f.messages.messages(f.actor(1,0),id,null,0L,30).items().stream().map(MessageView::sequence).toList());
            assertEquals(1,f.events.size());
        }finally{release.countDown();}
    }
    @Test void concurrentLimitIsSharedAcrossInstancesAndReadCursorNeverRegresses() throws Exception {
        long id=open();var instances=List.of(f,instance(),instance(),instance());var start=new CountDownLatch(1);int accepted=0;
        try(var pool=Executors.newFixedThreadPool(8)){
            List<Future<Integer>> results=new ArrayList<>();
            for(int i=0;i<40;i++){var worker=instances.get(i%4);results.add(pool.submit(()->{start.await();try{worker.messages.send(f.actor(1,0),id,text("new"));return 200;}catch(ChatException e){return e.status();}}));}
            start.countDown();for(var future:results){int status=future.get(15,TimeUnit.SECONDS);if(status==200)accepted++;else assertEquals(429,status);}
        }
        assertEquals(30,accepted);assertEquals(30,f.db.queryForObject("SELECT COUNT(*) FROM chat_message",Integer.class));
        var other=instance();concurrent(()->f.conversations.markRead(f.actor(2,0),id,30),()->other.conversations.markRead(f.actor(2,0),id,1));
        assertEquals(30,f.db.queryForObject("SELECT last_read_sequence FROM chat_conversation_member WHERE user_id=2",Long.class));assertEquals(0,f.conversations.unread(f.actor(2,0)).unreadCount());
    }
    @Test void quotaAndSequenceRollbackOnActualMySqlConstraintFailure() {
        long id=open();f.db.execute("ALTER TABLE chat_message ADD CONSTRAINT test_reject_chat CHECK(JSON_UNQUOTE(JSON_EXTRACT(content,'$.text')) <> 'reject')");
        try { assertThrows(RuntimeException.class,()->f.messages.send(f.actor(1,0),id,text("reject")));assertEquals(0,f.db.queryForObject("SELECT COUNT(*) FROM chat_send_rate_window",Integer.class));assertEquals(0,f.db.queryForObject("SELECT last_sequence FROM chat_conversation WHERE conversation_id=?",Long.class,id));assertTrue(f.events.isEmpty()); }
        finally{f.db.execute("ALTER TABLE chat_message DROP CHECK test_reject_chat");}
    }
    @Test void expiredQuotaCleanupIsBoundedAndSchemaUsesInnoDb() {
        long id=open();f.messages.send(f.actor(1,0),id,text("😀".repeat(2000)));
        f.db.update("UPDATE chat_send_rate_window SET expires_at='2026-10-10 00:00:00'");
        assertEquals(1,f.rates.purgeExpired(f.clock.instant(),1));assertEquals(1,f.db.queryForObject("SELECT COUNT(*) FROM chat_send_rate_window",Integer.class));
        assertEquals(4,f.db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'chat_%' AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci'",Integer.class));
    }
    @Test void millisecondTimestampIsStableAcrossRetry() {
        long id=open();var precise=java.time.Clock.fixed(java.time.Instant.parse("2026-10-11T10:00:00.123456Z"),java.time.ZoneOffset.UTC);
        var service=new ChatMessageService(f.conversations,f.relationships,f.session.getMapper(ChatConversationMapper.class),f.session.getMapper(ChatMessageMapper.class),f.rates,f.manager,e->{},precise);
        var request=text("timestamp");var first=service.send(f.actor(1,0),id,request);
        assertEquals(java.time.Instant.parse("2026-10-11T10:00:00.123Z"),first.createdAt());assertEquals(first,service.send(f.actor(1,0),id,request));
    }
}
