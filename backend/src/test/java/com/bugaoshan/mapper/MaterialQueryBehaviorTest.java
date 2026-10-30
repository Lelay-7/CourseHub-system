package com.bugaoshan.mapper;

import com.bugaoshan.entity.Material;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialQueryBehaviorTest {
    private SqlSession session() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:material_" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER", "sa", "");
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/MaterialMapper.xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, config, resource, config.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(config).openSession(true);
    }

    @Test void searchIncludesPublicUploaderNameAndCountMatchesAcrossPages() throws Exception {
        try (var db=session(); var sql=db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE user(user_id BIGINT, nickname VARCHAR(100), username VARCHAR(100), is_deleted INT)");
            sql.execute("CREATE TABLE material(material_id BIGINT PRIMARY KEY, material_name VARCHAR(100), keyword VARCHAR(100), uploader_id BIGINT, material_type INT, course_id BIGINT, download_score INT, status INT, is_deleted INT, create_time TIMESTAMP)");
            sql.execute("CREATE TABLE favorite(user_id BIGINT,target_type INT,target_id BIGINT)");
            sql.execute("INSERT INTO user VALUES(7,'上传者甲','uploader_a',0)");
            for(int i=1;i<=25;i++) sql.execute("INSERT INTO material VALUES("+i+",'Notes','math',7,1,1,0,1,0,CURRENT_TIMESTAMP)");
            Map<String,Object> args=new HashMap<>(); args.put("keyword","上传者甲"); args.put("offset",20); args.put("limit",20); args.put("viewerId",8L);
            List<Material> rows=db.selectList("com.bugaoshan.mapper.MaterialMapper.selectList",args);
            assertEquals(5,rows.size(),"Uploader search must filter the entire result before pagination");
            assertEquals(25L,((Number)db.selectOne("com.bugaoshan.mapper.MaterialMapper.countList",args)).longValue());
        }
    }

    @Test void auditOnlyChangesTheExpectedPendingRevisionOnce() throws Exception {
        try(var db=session();var sql=db.getConnection().createStatement()) {
            String id="com.bugaoshan.mapper.MaterialMapper.reviewRevision";
            assertTrue(db.getConfiguration().hasStatement(id),"Review must target an explicit submission revision");
            sql.execute("CREATE TABLE material(material_id BIGINT,status INT,revision INT,reject_reason VARCHAR(500),update_time TIMESTAMP,is_deleted INT)");
            sql.execute("INSERT INTO material VALUES(1,0,2,NULL,CURRENT_TIMESTAMP,0)");
            assertEquals(0,db.update(id,Map.of("materialId",1L,"revision",1,"status",1,"reason","")));
            assertEquals(1,db.update(id,Map.of("materialId",1L,"revision",2,"status",1,"reason","")));
            assertEquals(0,db.update(id,Map.of("materialId",1L,"revision",2,"status",1,"reason","")));
        }
    }
}
