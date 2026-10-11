package com.bugaoshan.chat;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.bugaoshan.dto.ChatDTO.Actor;
import com.bugaoshan.mapper.*;
import com.bugaoshan.service.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import javax.sql.DataSource;
import java.time.*;
import java.util.UUID;
public class ChatTestFixture {
    public JdbcTemplate db;
    public UserMapper users;
    public FriendMapper friends;
    public FriendRelationshipService relationships;
    public ChatConversationService conversations;
    public ChatMessageService messages;
    public ChatSendRateLimiter rates;
    public com.bugaoshan.config.ChatConfig config=new com.bugaoshan.config.ChatConfig();
    public java.util.List<com.bugaoshan.chat.ChatMessageCreated> events=new java.util.concurrent.CopyOnWriteArrayList<>();
    public DataSourceTransactionManager manager;
    public DataSource source;
    public SqlSessionTemplate session;
    public Clock clock=Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"),ZoneOffset.UTC);
    public ChatTestFixture() throws Exception { this(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa",""),true); }
    public ChatTestFixture(DataSource source,boolean initialize) throws Exception {
        this.source=source; db=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source);
        if(initialize) try(var c=source.getConnection(); var r=new java.io.InputStreamReader(new ClassPathResource("db/private-chat-h2.sql").getInputStream(),java.nio.charset.StandardCharsets.UTF_8)) { org.h2.tools.RunScript.execute(c,r); }
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath:mapper/*Mapper.xml"));
        session=new SqlSessionTemplate(factory.getObject()); users=session.getMapper(UserMapper.class); friends=session.getMapper(FriendMapper.class);
        relationships=new FriendRelationshipService(users,friends,manager);
        conversations=new ChatConversationService(users,relationships,session.getMapper(ChatConversationMapper.class),manager,clock);
        rates=new ChatSendRateLimiter(session.getMapper(ChatRateLimitMapper.class),this.config,clock);
        messages=new ChatMessageService(conversations,relationships,session.getMapper(ChatConversationMapper.class),session.getMapper(ChatMessageMapper.class),rates,manager,e->events.add((com.bugaoshan.chat.ChatMessageCreated)e),clock);
    }
    public void seedUsers() {
        db.update("INSERT INTO user(user_id,username,password_hash,nickname,status,user_type,phone,email,real_name,student_no,department) VALUES(1,'a','test-hash','甲',1,1,'secret1','private@example.test','hidden','secret-no','计算机'),(2,'b','test-hash','乙',1,2,'secret2','private@example.test','hidden','secret-no','计算机'),(3,'admin','test-hash','管理员',1,3,NULL,NULL,NULL,NULL,NULL)");
    }
    public Actor actor(long id,int version) { return new Actor(id,version); }
    public void friend(long a,long b) { db.update("INSERT INTO friend(user_id,target_id,status) VALUES(?,?,1)",a,b); }
    public void seedMessage(long conversation,long sequence,long sender,String text) {
        db.update("INSERT INTO chat_message(conversation_id,sequence,sender_id,client_message_id,message_type,content,created_at) VALUES(?,?,?,?,?,?,?)",conversation,sequence,sender,UUID.randomUUID().toString(),"TEXT","{\"text\":\""+text+"\"}",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        db.update("UPDATE chat_conversation SET last_sequence=? WHERE conversation_id=?",sequence,conversation);
    }
}
