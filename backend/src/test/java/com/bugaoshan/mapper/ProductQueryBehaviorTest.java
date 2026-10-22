package com.bugaoshan.mapper;

import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.Goods;
import com.bugaoshan.entity.MaterialReport;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production queries against independent fixtures, including records beyond page one. */
class ProductQueryBehaviorTest {
    private SqlSession session(String mapper) throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:product_" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER,ORDER", "sa", "");
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/" + mapper + ".xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, config, resource, config.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(config).openSession(true);
    }

    @Test void priceOrderingHappensBeforePagination() throws Exception {
        try (var db = session("GoodsMapper"); Statement sql = db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE goods(goods_id BIGINT PRIMARY KEY, price_score INT, category INT, status INT, is_deleted INT, create_time TIMESTAMP)");
            for (int i = 1; i <= 51; i++) sql.execute("INSERT INTO goods VALUES(" + i + "," + i + ",1,1,0,DATEADD('SECOND'," + i + ",TIMESTAMP '2026-10-01 00:00:00'))");
            List<Goods> rows = db.selectList("com.bugaoshan.mapper.GoodsMapper.selectList",
                    Map.of("offset",20,"limit",20,"sortBy","price_score"));
            assertEquals(21L, rows.get(0).getGoodsId());
            assertEquals(40L, rows.get(19).getGoodsId());
        }
    }

    @Test void deletedCourseStillReservesItsUniqueCode() throws Exception {
        try (var db = session("CourseMapper"); Statement sql = db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE course(course_id BIGINT PRIMARY KEY, course_code VARCHAR(50), is_deleted INT)");
            sql.execute("INSERT INTO course VALUES(8,'CS-RESERVED',1)");
            Course course = db.selectOne("com.bugaoshan.mapper.CourseMapper.selectByCourseCode", Map.of("courseCode","CS-RESERVED"));
            assertNotNull(course, "A deleted row must not evade the unique-code validation");
            assertEquals(8L, course.getCourseId());
        }
    }

    @Test void handledReportRemainsVisibleAfterMaterialIsDeactivated() throws Exception {
        try (var db = session("MaterialReportMapper"); Statement sql = db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE material(material_id BIGINT PRIMARY KEY, material_name VARCHAR(100), is_deleted INT, status INT)");
            sql.execute("CREATE TABLE material_report(report_id BIGINT PRIMARY KEY, material_id BIGINT, reporter_id BIGINT, status INT, reason VARCHAR(100), create_time TIMESTAMP)");
            sql.execute("INSERT INTO material VALUES(9,'Reported file',0,3)");
            sql.execute("INSERT INTO material_report VALUES(1,9,8,1,'Invalid content',CURRENT_TIMESTAMP)");
            List<MaterialReport> rows = db.selectList("com.bugaoshan.mapper.MaterialReportMapper.selectAll", Map.of("offset",0,"limit",20));
            assertEquals(1, rows.size(), "Handling a report must not erase it from the review history");
            assertEquals(1L, ((Number) db.selectOne("com.bugaoshan.mapper.MaterialReportMapper.countAll")).longValue());
        }
    }

    @Test void monthlySummaryCountsAllRecordsAndSeparatesRefundsAndAccounts() throws Exception {
        try (var db = session("ScoreRecordMapper"); Statement sql = db.getConnection().createStatement()) {
            String id = "com.bugaoshan.mapper.ScoreRecordMapper.summarizeMonth";
            assertTrue(db.getConfiguration().hasStatement(id), "Monthly totals need a complete server-side aggregate");
            sql.execute("CREATE TABLE score_record(user_id BIGINT, change_type INT, change_amount INT, create_time TIMESTAMP)");
            for (int i=0;i<120;i++) sql.execute("INSERT INTO score_record VALUES(8,1,2,'2026-10-01 12:00:00')");
            sql.execute("INSERT INTO score_record VALUES(8,2,-7,'2026-10-02 12:00:00'),(8,3,15,'2026-10-03 12:00:00'),(9,1,999,'2026-10-01 00:00:00'),(8,1,100,'2026-11-01 00:00:00')");
            Map<String,Object> totals = db.selectOne(id, Map.of("userId",8L,
                    "start",LocalDateTime.of(2026,10,1,0,0),"end",LocalDateTime.of(2026,11,1,0,0)));
            Map<String,Number> normalized = new HashMap<>();
            totals.forEach((key,value)->normalized.put(key.toLowerCase(Locale.ROOT),(Number)value));
            assertEquals(240L, normalized.get("earned").longValue());
            assertEquals(7L, normalized.get("spent").longValue());
            assertEquals(15L, normalized.get("refunded").longValue());
        }
    }

    @Test void completingOrderPersistsNoteOnceAndCannotOverwriteCancelledOrder() throws Exception {
        try (var db = session("OrderMapper"); Statement sql = db.getConnection().createStatement()) {
            String id = "com.bugaoshan.mapper.OrderMapper.completeWithNote";
            assertTrue(db.getConfiguration().hasStatement(id), "Fulfilment and its explanation must be persisted atomically");
            sql.execute("CREATE TABLE `order`(order_id BIGINT PRIMARY KEY,status INT,is_deleted INT,fulfillment_note VARCHAR(1000),complete_time TIMESTAMP,update_time TIMESTAMP)");
            sql.execute("INSERT INTO `order`(order_id,status,is_deleted) VALUES(1,1,0),(2,4,0)");
            assertEquals(1, db.update(id,Map.of("orderId",1L,"note","Collect at the test desk")));
            assertEquals(0, db.update(id,Map.of("orderId",1L,"note","Overwrite")));
            assertEquals(0, db.update(id,Map.of("orderId",2L,"note","Cancelled")));
            var rows=sql.executeQuery("SELECT fulfillment_note,status FROM `order` WHERE order_id=1");
            assertTrue(rows.next());
            assertEquals("Collect at the test desk",rows.getString(1));
            assertEquals(3,rows.getInt(2));
        }
    }

    @Test void courseContextFiltersSquareByStableIdBeforePagination() throws Exception {
        try (var db = session("SquarePostMapper"); Statement sql = db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE user(user_id BIGINT, nickname VARCHAR(50), avatar_url VARCHAR(100))");
            sql.execute("CREATE TABLE square_post(post_id BIGINT, user_id BIGINT, course_id BIGINT, course_name VARCHAR(100), status INT, is_deleted INT, create_time TIMESTAMP)");
            sql.execute("INSERT INTO user VALUES(8,'Student',NULL)");
            sql.execute("INSERT INTO square_post VALUES(1,8,11,'Same name',1,0,'2026-10-01'),(2,8,12,'Same name',1,0,'2026-10-02'),(3,8,11,'Renamed course',1,0,'2026-10-03')");
            Map<String,Object> filters = new HashMap<>();
            filters.put("status",1); filters.put("courseId",11L); filters.put("offset",1); filters.put("limit",1);
            List<com.bugaoshan.entity.SquarePost> rows = db.selectList("com.bugaoshan.mapper.SquarePostMapper.selectAllWithUser",filters);
            assertEquals(1L,rows.get(0).getPostId());
            assertEquals(2L,((Number) db.selectOne("com.bugaoshan.mapper.SquarePostMapper.countAllWithUser",filters)).longValue());
        }
    }

    @Test void feedbackManagementReturnsOnlyTheRequestedPage() throws Exception {
        try (var db = session("FeedbackMapper"); Statement sql = db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE user(user_id BIGINT, nickname VARCHAR(50))");
            sql.execute("CREATE TABLE feedback(feedback_id BIGINT, user_id BIGINT, status INT, create_time TIMESTAMP)");
            for (int i=1;i<=21;i++) sql.execute("INSERT INTO feedback VALUES("+i+",8,0,DATEADD('SECOND',"+i+",TIMESTAMP '2026-10-01 00:00:00'))");
            List<com.bugaoshan.entity.Feedback> rows = db.selectList("com.bugaoshan.mapper.FeedbackMapper.selectAllWithUser", Map.of("status",0,"offset",20,"limit",20));
            assertEquals(1, rows.size());
            assertEquals(1L, rows.get(0).getFeedbackId());
        }
    }

    @Test void reportStatusFilterMatchesRowsAndCount() throws Exception {
        try (var db = session("MaterialReportMapper"); Statement sql = db.getConnection().createStatement()) {
            sql.execute("CREATE TABLE material(material_id BIGINT, material_name VARCHAR(100))");
            sql.execute("CREATE TABLE material_report(report_id BIGINT, material_id BIGINT, reporter_id BIGINT, status INT, create_time TIMESTAMP)");
            sql.execute("INSERT INTO material_report VALUES(1,8,2,0,CURRENT_TIMESTAMP),(2,8,2,1,CURRENT_TIMESTAMP),(3,8,2,2,CURRENT_TIMESTAMP)");
            var filters = Map.of("status",1,"offset",0,"limit",20);
            List<MaterialReport> rows = db.selectList("com.bugaoshan.mapper.MaterialReportMapper.selectAll", filters);
            assertEquals(1,rows.size()); assertEquals(2L,rows.get(0).getReportId());
            assertEquals(1L,((Number)db.selectOne("com.bugaoshan.mapper.MaterialReportMapper.countAll",filters)).longValue());
        }
    }
}
