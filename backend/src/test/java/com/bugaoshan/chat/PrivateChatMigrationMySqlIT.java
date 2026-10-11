package com.bugaoshan.chat;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Legacy fixture is copied from f6ebb7c, not reconstructed using new generated constraints. */
class PrivateChatMigrationMySqlIT {
    DataSource source; JdbcTemplate db;
    @BeforeEach void setup() throws Exception { source=PrivateChatE2eApplication.guardedSource();db=new JdbcTemplate(source); }
    String resource(String path) throws Exception { return new String(new ClassPathResource(path).getInputStream().readAllBytes(),StandardCharsets.UTF_8); }
    void reset(String init) throws Exception {
        PrivateChatE2eApplication.guardedSource();
        for(String name:db.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()",String.class)){
            if(name.equals("private_chat_test_guard"))continue;
            assertTrue(name.matches("[a-z_]+"));db.execute("DROP TABLE `"+name+"`");
        }
        String database=db.queryForObject("SELECT DATABASE()",String.class);assertTrue(database.matches("coursehub_chat_check_[a-f0-9]{32}"));
        try(var c=source.getConnection()){ScriptUtils.executeSqlScript(c,new org.springframework.core.io.support.EncodedResource(new ByteArrayResource(resource(init).replace("bgss_xtt",database).getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));}
        migrate("db/migrations/2026-10-10-account-recovery.sql");
    }
    void migrate(String path) throws Exception {
        String script=resource(path);
        if(!script.contains("DELIMITER")){try(var c=source.getConnection()){ScriptUtils.executeSqlScript(c,new org.springframework.core.io.support.EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));}return;}
        String delimiter=";";StringBuilder statement=new StringBuilder();
        try(var c=source.getConnection();var s=c.createStatement()){
            for(String line:script.split("\\R")){
                String trimmed=line.trim();if(trimmed.startsWith("--")||trimmed.isEmpty())continue;
                if(trimmed.startsWith("DELIMITER ")){assertTrue(statement.toString().isBlank());delimiter=trimmed.substring(10).trim();continue;}
                statement.append(line).append('\n');
                if(trimmed.endsWith(delimiter)){String sql=statement.toString().trim();s.execute(sql.substring(0,sql.length()-delimiter.length()));statement.setLength(0);}
            }
            assertTrue(statement.toString().isBlank(),"Migration parser must execute every statement");
        }
    }
    @AfterEach void restoreFreshSchema() throws Exception { reset("db/init.sql");migrate("db/migrations/2026-10-11-private-chat.sql"); }
    @Test void legacyDuplicatesPreferAcceptedThenLowestIdAndPreserveUsers() throws Exception {
        reset("db/private-chat-legacy-mysql.sql");
        var users=db.queryForList("SELECT * FROM user ORDER BY user_id");
        List<Long> ids=db.queryForList("SELECT user_id FROM user ORDER BY user_id LIMIT 4",Long.class);assertEquals(4,ids.size());
        long a=ids.get(0),b=ids.get(1),c=ids.get(2),d=ids.get(3);
        db.update("DELETE FROM friend");
        db.update("INSERT INTO friend(friend_id,user_id,target_id,status) VALUES(100,?,?,0),(101,?,?,1),(102,?,?,0),(103,?,?,0)",a,b,b,a,c,d,d,c);
        db.update("UPDATE user SET status=0,is_deleted=1 WHERE user_id=?",d);
        users=db.queryForList("SELECT * FROM user ORDER BY user_id");
        migrate("db/migrations/2026-10-11-private-chat.sql");
        assertEquals(List.of(101L,102L),db.queryForList("SELECT friend_id FROM friend ORDER BY friend_id",Long.class));
        assertEquals(users,db.queryForList("SELECT * FROM user ORDER BY user_id"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM chat_conversation",Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("INSERT INTO friend(user_id,target_id,status) VALUES(?,?,0)",a,b));
    }
    @Test void migrationStopsBeforeChangingInvalidRows() throws Exception {
        for(String invalid:List.of("self","orphan","status")){
            reset("db/private-chat-legacy-mysql.sql");long id=db.queryForObject("SELECT MIN(user_id) FROM user",Long.class);
            db.update("DELETE FROM friend");
            db.update("INSERT INTO friend(user_id,target_id,status) VALUES(?,?,?)",id,invalid.equals("self")?id:invalid.equals("orphan")?999999L:id+1,invalid.equals("status")?3:0);
            var before=db.queryForList("SELECT * FROM friend");
            assertThrows(java.sql.SQLException.class,()->migrate("db/migrations/2026-10-11-private-chat.sql"));
            assertEquals(before,db.queryForList("SELECT * FROM friend"));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='friend' AND column_name='user_low_id'",Integer.class));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'chat_%'",Integer.class));
        }
    }
    @Test void freshInitializationAndMigrationChainAreRepeatable() throws Exception {
        reset("db/init.sql");var before=db.queryForList("SELECT * FROM user ORDER BY user_id");
        migrate("db/migrations/2026-10-11-private-chat.sql");migrate("db/migrations/2026-10-11-private-chat.sql");
        assertEquals(before,db.queryForList("SELECT * FROM user ORDER BY user_id"));
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'chat_%'",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='chat_message' AND index_name='uk_chat_client' AND non_unique=0",Integer.class));
    }
}
