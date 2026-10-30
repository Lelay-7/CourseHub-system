package com.bugaoshan.material;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Material;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialReviewWorkflowTest {
    @TempDir Path directory;
    MaterialWorkflowFixture f;
    protected MaterialWorkflowFixture fixture(Path directory) throws Exception {
        return new MaterialWorkflowFixture(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()
                +";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa",""),directory,true);
    }
    @BeforeEach void setup() throws Exception { f=fixture(directory); }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    MockMultipartFile file(String name,String content) { return new MockMultipartFile("file",name,"application/pdf",content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    long upload() {
        var result=f.service.uploadMaterial(1L,file("original.pdf","original"),"原始资料",1,1L,3,"数学","第一版");
        return ((Material)((Map<?,?>)result.getData()).get("material")).getMaterialId();
    }
    int value(String sql,long id) { return f.db.queryForObject(sql,Integer.class,id); }
    String contents(com.bugaoshan.storage.DownloadFile file) throws Exception {
        try(var stream=file.resource().getInputStream()) { return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); }
    }

    @Test void advertisedCsvTypeSupportsUploadReplacementReviewAndDownload() throws Exception {
        var original = new MockMultipartFile("file", "课程.csv", "text/csv", "课程,学分\n数学,3".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = f.service.uploadMaterial(1L, original, "课程表格", 3, 1L, 0, "", "");
        long id = ((Material)((Map<?,?>)result.getData()).get("material")).getMaterialId();
        assertEquals("课程,学分\n数学,3", contents(f.review.file(id, 1, 3L)));
        f.review.reject(id, 1, 3L, "请补充课程");
        var replacement = new MockMultipartFile("file", "课程V2.csv", "text/csv", "课程,学分\n数学,4".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        f.service.resubmitMaterial(id, 1L, 1, replacement, "课程表格", 3, 1L, 0, "", "已修正");
        f.review.approve(id, 2, 3L);
        assertEquals("课程,学分\n数学,4", contents(f.service.downloadMaterial(id, 2L)));
        assertEquals("text/csv", f.materials.selectById(id).getContentType());
    }

    @Test void csvSupportDoesNotAcceptOtherTextMimeTypes() {
        var html = new MockMultipartFile("file", "page.html", "text/html", "<p>not a supported upload</p>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> f.service.uploadMaterial(1L, html, "网页", 3, 1L, 0, "", ""));
    }

    @Test void completeLifecycleKeepsBothFilesHistoryAndExactlyOneReward() throws Exception {
        long id=upload();
        assertEquals("original",contents(f.review.file(id,1,3L)));
        assertEquals(0,value("SELECT download_count FROM material WHERE material_id=?",id));
        assertEquals(20,value("SELECT score_balance FROM user WHERE user_id=?",1));
        assertThrows(BusinessException.class,()->f.service.getMaterialDetail(id,2L));
        assertThrows(BusinessException.class,()->f.service.downloadMaterial(id,2L));
        f.review.reject(id,1,3L,"需要更正内容");
        f.service.resubmitMaterial(id,1L,1,file("replacement.pdf","revised"),"修正资料",1,1L,3,"代数","第二版");
        assertEquals(2,f.materials.selectById(id).getRevision());
        assertNull(f.materials.selectById(id).getRejectReason());
        assertEquals(2,f.history.selectRevisions(id).size());
        assertEquals("original",contents(f.review.file(id,1,3L)));
        assertEquals("revised",contents(f.review.file(id,2,3L)));
        assertThrows(BusinessException.class,()->f.review.approve(id,1,3L));
        f.review.approve(id,2,3L);
        assertThrows(BusinessException.class,()->f.review.approve(id,2,4L));
        assertEquals(22,value("SELECT score_balance FROM user WHERE user_id=?",1));
        assertEquals(1,value("SELECT COUNT(*) FROM score_record WHERE related_id=? AND change_type=1",id));
        assertEquals(List.of("approve","reject"),f.history.selectAuditHistory(id).stream().map(a->a.getAction()).toList());
        assertEquals("revised",contents(f.service.downloadMaterial(id,2L)));
        assertEquals(17,value("SELECT score_balance FROM user WHERE user_id=?",2));
        f.service.downloadMaterial(id,2L);
        assertEquals(17,value("SELECT score_balance FROM user WHERE user_id=?",2));
    }

    @Test void permissionsAndVersionsAreCheckedBeforeAnyFileMutation() throws Exception {
        long id=upload(); f.review.reject(id,1,3L,"请修改");
        assertThrows(BusinessException.class,()->f.review.detail(id,2L));
        assertThrows(BusinessException.class,()->f.review.file(id,1,1L));
        assertThrows(BusinessException.class,()->f.review.approve(id,1,2L));
        assertThrows(BusinessException.class,()->f.service.resubmitMaterial(id,2L,1,file("bad.pdf","bad"),"修改",1,1L,0,"",""));
        assertThrows(BusinessException.class,()->f.service.resubmitMaterial(id,1L,99,file("bad.pdf","bad"),"修改",1,1L,0,"",""));
        assertThrows(BusinessException.class,()->f.service.resubmitMaterial(id,1L,1,null,"修改",1,999L,0,"",""));
        try(var paths=Files.walk(directory)) { assertEquals(1,paths.filter(Files::isRegularFile).count()); }
        assertEquals(2,f.materials.selectById(id).getStatus());
        assertEquals(1,f.history.selectRevisions(id).size());
    }

    @Test void resubmitRollbackRemovesOnlyNewFileAndPreservesOriginalData() throws Exception {
        long id=upload(); f.review.reject(id,1,3L,"请修改");
        // A real database uniqueness failure after updating material exercises rollback and filesystem cleanup.
        Material reserved=f.materials.selectById(id); reserved.setRevision(2); f.history.insertSnapshot(reserved);
        assertThrows(RuntimeException.class,()->f.service.resubmitMaterial(id,1L,1,file("bad.pdf","must rollback"),"不可保存",1,1L,0,"",""));
        assertEquals(1,f.materials.selectById(id).getRevision());
        assertEquals("原始资料",f.materials.selectById(id).getMaterialName());
        assertEquals("请修改",f.materials.selectById(id).getRejectReason());
        assertEquals("original",contents(f.review.file(id,1,3L)));
        try(var paths=Files.walk(directory)) { assertEquals(1,paths.filter(Files::isRegularFile).count()); }
    }

    @Test void metadataOnlyResubmissionReusesTheOriginalFileAndKeepsRejectionHistory() throws Exception {
        long id=upload(); f.review.reject(id,1,3L,"补充说明");
        f.service.resubmitMaterial(id,1L,1,null,"新名称",1,1L,0,"新增关键词","已补充");
        assertEquals("original",contents(f.review.file(id,2,3L)));
        assertEquals("补充说明",f.history.selectAuditHistory(id).getFirst().getReason());
        try(var paths=Files.walk(directory)) { assertEquals(1,paths.filter(Files::isRegularFile).count()); }
    }

    @Test void concurrentApproversCommitOneRewardAndOneAuditEntry() throws Exception {
        long id=upload(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> outcomes=new ArrayList<>();
            for(long admin:List.of(3L,4L)) outcomes.add(pool.submit(()->{start.await();try{f.review.approve(id,1,admin);return true;}catch(BusinessException expected){return false;}}));
            start.countDown(); int accepted=0;
            for(var outcome:outcomes) if(outcome.get(10,TimeUnit.SECONDS)) accepted++;
            assertEquals(1,accepted);
        }
        assertEquals(22,value("SELECT score_balance FROM user WHERE user_id=?",1));
        assertEquals(1,f.history.selectAuditHistory(id).size());
        assertEquals(1,value("SELECT COUNT(*) FROM score_record WHERE related_id=?",id));
    }

    @Test void invalidRejectionAndRepeatedDeactivationNeverCorruptState() {
        long id=upload();
        assertThrows(BusinessException.class,()->f.review.reject(id,1,3L,"  "));
        assertThrows(BusinessException.class,()->f.review.reject(id,1,3L,"x".repeat(501)));
        assertEquals(0,f.materials.selectById(id).getStatus());
        f.review.approve(id,1,3L); f.review.deactivate(id,3L,"举报成立");
        assertThrows(BusinessException.class,()->f.review.deactivate(id,4L,"重复处理"));
        assertEquals(3,f.materials.selectById(id).getStatus());
        assertEquals(2,f.history.selectAuditHistory(id).size());
        assertThrows(BusinessException.class,()->f.service.downloadMaterial(id,2L));
    }

    @Test void favoritesAreViewerSpecificAndSearchUsesOnlyThePublicName() {
        long id=upload(); f.review.approve(id,1,3L);
        f.db.update("INSERT INTO favorite(user_id,target_type,target_id) VALUES(2,2,?)",id);
        var reader=f.materials.selectList(null,null,"上传者甲",null,null,"desc",0,10,2L);
        var owner=f.materials.selectList(null,null,"上传者甲",null,null,"desc",0,10,1L);
        assertEquals(1,reader.size()); assertTrue(reader.getFirst().getIsFavorited()); assertFalse(owner.getFirst().getIsFavorited());
        assertEquals("上传者甲",reader.getFirst().getUploaderName());
    }
}
