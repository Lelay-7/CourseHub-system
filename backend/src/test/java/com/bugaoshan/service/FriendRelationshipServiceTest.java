package com.bugaoshan.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.bugaoshan.common.BusinessException;
import com.bugaoshan.mapper.FriendMapper;
import com.bugaoshan.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class FriendRelationshipServiceTest {
    JdbcTemplate db;
    UserMapper users;
    FriendMapper friends;
    FriendRelationshipService service;
    DataSourceTransactionManager manager;
    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
        try (var c=source.getConnection(); var reader=new java.io.InputStreamReader(new ClassPathResource("db/private-chat-h2.sql").getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            org.h2.tools.RunScript.execute(c,reader);
        }
        db.update("INSERT INTO user(user_id,username,status) VALUES(1,'a',1),(2,'b',1),(3,'disabled',0)");
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/UserMapper.xml"),new ClassPathResource("mapper/FriendMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject()); users=session.getMapper(UserMapper.class); friends=session.getMapper(FriendMapper.class);
        service=new FriendRelationshipService(users,friends,manager);
    }
    long insertFriend(long a,long b,int status) {
        db.update("INSERT INTO friend(user_id,target_id,status) VALUES(?,?,?)",a,b,status);
        return db.queryForObject("SELECT friend_id FROM friend WHERE user_id=? AND target_id=?",Long.class,a,b);
    }
    @Test void reverseAcceptedDoesNotCreatePending() {
        insertFriend(2,1,1);
        assertThrows(BusinessException.class,()->service.addFriend(1,2));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM friend",Integer.class));
        assertEquals(1,db.queryForObject("SELECT status FROM friend",Integer.class));
    }
    @Test void invalidTargetDoesNotInsert() {
        for (long target: new long[]{1,3,99}) assertThrows(BusinessException.class,()->service.addFriend(1,target));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM friend",Integer.class));
    }
    @Test void onlyRecipientMayAcceptPending() {
        long id=insertFriend(1,2,0);
        assertThrows(BusinessException.class,()->service.accept(1,id));
        service.accept(2,id);
        assertEquals(1,db.queryForObject("SELECT status FROM friend",Integer.class));
        assertThrows(BusinessException.class,()->service.accept(2,id));
    }
    @Test void oppositePendingRequestIsAcceptedAndParticipantDeletionIsChecked() {
        long id=insertFriend(2,1,0);
        assertTrue(service.addFriend(1,2).acceptedExistingRequest());
        assertThrows(BusinessException.class,()->service.remove(3,id));
        service.remove(1,id);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM friend",Integer.class));
        assertThrows(BusinessException.class,()->service.remove(1,id));
    }
    @Test void pairLocksAreTakenInAscendingOrder() {
        List<Long> locks=new ArrayList<>();
        UserMapper observed=(UserMapper)Proxy.newProxyInstance(UserMapper.class.getClassLoader(),new Class[]{UserMapper.class},(p,m,args)->{
            if(m.getName().equals("selectForUpdate")) locks.add((Long)args[0]);
            return m.invoke(users,args);
        });
        var observedService=new FriendRelationshipService(observed,friends,manager);
        new TransactionTemplate(manager).executeWithoutResult(tx->observedService.lockUsers(2,1));
        assertEquals(List.of(1L,2L),locks);
    }
}
