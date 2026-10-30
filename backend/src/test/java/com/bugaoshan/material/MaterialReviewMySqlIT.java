package com.bugaoshan.material;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real MySQL suite. Only a guarded disposable schema can be reset by this fixture. */
class MaterialReviewMySqlIT extends MaterialReviewWorkflowTest {
    static DataSource guardedSource() throws Exception {
        String url=required("COURSEHUB_MATERIAL_MYSQL_URL"), nonce=required("COURSEHUB_MATERIAL_MYSQL_NONCE");
        require(url.startsWith("jdbc:mysql://"),"Not a MySQL URL");
        URI uri=URI.create(url.substring(5));
        require("127.0.0.1".equals(uri.getHost()) && uri.getPort()>=1024 && uri.getPort()<=65535
                && uri.getUserInfo()==null && uri.getFragment()==null
                && uri.getPath().matches("/coursehub_material_check_[a-f0-9]{32}"),"Refusing non-disposable material database");
        require(nonce.matches("[a-f0-9]{32}"),"Invalid material database nonce");
        var source=new DriverManagerDataSource(url,required("COURSEHUB_MATERIAL_MYSQL_USER"),
                System.getenv().getOrDefault("COURSEHUB_MATERIAL_MYSQL_PASSWORD",""));
        try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT nonce FROM material_test_guard")) {
            require(c.getMetaData().getDatabaseMajorVersion()==8 && rows.next() && nonce.equals(rows.getString(1)) && !rows.next(),"Material database guard mismatch");
        }
        return source;
    }
    @Override protected MaterialWorkflowFixture fixture(Path directory) throws Exception {
        DataSource source=guardedSource(); var db=new JdbcTemplate(source);
        for(String table:List.of("material_audit_log","material_revision","material_download","material_report","favorite","score_record","material","course","user")) {
            db.execute("DROP TABLE IF EXISTS `"+table+"`");
        }
        return new MaterialWorkflowFixture(source,directory,true);
    }

    @Test void migrationPreservesLegacyStatesBalancesRewardsAndRejectedReason() throws Exception {
        long published=upload(),rejected=upload(),pendingWithOldReward=upload();
        f.db.update("UPDATE material SET status=1 WHERE material_id=?",published);
        f.db.update("UPDATE material SET status=2,reject_reason='旧驳回原因' WHERE material_id=?",rejected);
        f.db.update("INSERT INTO score_record(user_id,change_type,change_amount,balance_after,source_desc,related_id) VALUES(1,1,2,20,'上传资料审核通过：旧资料',?)",pendingWithOldReward);
        f.db.execute("DROP TABLE material_revision"); f.db.execute("DROP TABLE material_audit_log");
        f.db.execute("ALTER TABLE material DROP COLUMN revision, DROP COLUMN upload_rewarded");
        new ResourceDatabasePopulator(new ClassPathResource("db/migrations/2026-10-15-material-review-closure.sql")).execute(guardedSource());
        assertEquals(1,f.materials.selectById(published).getStatus());
        assertEquals(2,f.materials.selectById(rejected).getStatus());
        assertEquals(0,f.materials.selectById(pendingWithOldReward).getStatus());
        assertEquals("旧驳回原因",f.history.selectRevision(rejected,1).getRejectReason());
        assertEquals(20,value("SELECT score_balance FROM user WHERE user_id=?",1));
        assertEquals(1,f.materials.selectById(published).getUploadRewarded());
        assertEquals(0,f.materials.selectById(rejected).getUploadRewarded());
        assertEquals(1,f.materials.selectById(pendingWithOldReward).getUploadRewarded());
        f.review.approve(pendingWithOldReward,1,3L);
        assertEquals(20,value("SELECT score_balance FROM user WHERE user_id=?",1));
        assertEquals(1,value("SELECT COUNT(*) FROM score_record WHERE related_id=?",pendingWithOldReward));
    }

    private static String required(String key) { String value=System.getenv(key); require(value!=null&&!value.isBlank(),"Missing "+key); return value; }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
}
