package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.entity.Material;
import com.bugaoshan.entity.MaterialReport;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.MaterialMapper;
import com.bugaoshan.mapper.MaterialDownloadMapper;
import com.bugaoshan.mapper.MaterialReportMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

    @Mock private MaterialMapper materialMapper;
    @Mock private FavoriteMapper favoriteMapper;
    @Mock private UserMapper userMapper;
    @Mock private ScoreRecordMapper scoreRecordMapper;
    @Mock private MaterialDownloadMapper materialDownloadMapper;
    @Mock private MaterialReportMapper materialReportMapper;
    @Mock private FileStorageService fileStorageService;

    @Test
    void otherUserCannotDownloadUnapprovedMaterial() {
        Material material = new Material();
        material.setMaterialId(3L);
        material.setUploaderId(10L);
        material.setStatus(0);
        when(materialMapper.selectById(3L)).thenReturn(material);
        MaterialServiceImpl service = new MaterialServiceImpl(
                materialMapper, favoriteMapper, userMapper, scoreRecordMapper,
                materialDownloadMapper, materialReportMapper, fileStorageService);

        assertThrows(BusinessException.class, () -> service.downloadMaterial(3L, 11L));
    }

    @Test
    void previouslyPaidDownloadDoesNotRequireCurrentBalanceOrChargeAgain() {
        Material material = new Material();
        material.setMaterialId(5L);
        material.setUploaderId(10L);
        material.setStatus(1);
        material.setDownloadScore(20);
        material.setStorageKey("materials/paid.pdf");
        material.setOriginalFileName("paid.pdf");
        material.setContentType("application/pdf");
        User user = new User();
        user.setUserId(11L);
        user.setStatus(1);
        user.setScoreBalance(0);
        when(materialMapper.selectById(5L)).thenReturn(material);
        when(userMapper.selectById(11L)).thenReturn(user);
        when(materialDownloadMapper.insertIgnore(5L, 11L, 20)).thenReturn(0);
        when(fileStorageService.load("materials/paid.pdf"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));
        MaterialServiceImpl service = new MaterialServiceImpl(
                materialMapper, favoriteMapper, userMapper, scoreRecordMapper,
                materialDownloadMapper, materialReportMapper, fileStorageService);

        var download = service.downloadMaterial(5L, 11L);

        assertEquals(0, download.scoreBalance());
        verify(userMapper, never()).deductScoreIfSufficient(11L, 20);
    }

    @Test
    void resubmittingRejectedMaterialClearsRejectReasonAtomically() {
        Material material = new Material();
        material.setMaterialId(6L);
        material.setUploaderId(11L);
        material.setStatus(2);
        material.setRejectReason("内容不完整");
        when(materialMapper.selectById(6L)).thenReturn(material);
        when(materialMapper.resubmitRejected(6L)).thenReturn(1);
        MaterialServiceImpl service = new MaterialServiceImpl(
                materialMapper, favoriteMapper, userMapper, scoreRecordMapper,
                materialDownloadMapper, materialReportMapper, fileStorageService);

        service.resubmitMaterial(6L, 11L);

        verify(materialMapper).resubmitRejected(6L);
        verify(materialMapper, never()).updateStatus(6L, 0);
    }

    @Test
    void reportRejectsInactiveMaterial() {
        Material material = new Material();
        material.setStatus(3);
        material.setIsDeleted(0);
        when(materialMapper.selectById(6L)).thenReturn(material);

        assertThrows(BusinessException.class,
                () -> service().reportMaterial(6L, 11L, report("invalid content")));

        verify(materialReportMapper, never()).insert(any(MaterialReport.class));
    }

    @Test
    void reportRejectsBlankReason() {
        when(materialMapper.selectById(6L)).thenReturn(publishedMaterial());

        assertThrows(BusinessException.class,
                () -> service().reportMaterial(6L, 11L, report("   ")));

        verify(materialReportMapper, never()).insert(any(MaterialReport.class));
    }

    @Test
    void reportRejectsReasonOverFiveHundredCharacters() {
        when(materialMapper.selectById(6L)).thenReturn(publishedMaterial());

        assertThrows(BusinessException.class,
                () -> service().reportMaterial(6L, 11L, report("x".repeat(501))));

        verify(materialReportMapper, never()).insert(any(MaterialReport.class));
    }

    @Test
    void reportRejectsDuplicatePendingReport() {
        when(materialMapper.selectById(6L)).thenReturn(publishedMaterial());
        when(materialReportMapper.existsPending(6L, 11L)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service().reportMaterial(6L, 11L, report("invalid content")));

        verify(materialReportMapper, never()).insert(any(MaterialReport.class));
    }

    @Test
    void validPriceFilterIsAppliedToListAndCount() {
        service().getMaterialList(1, 2L, "exam", "score",
                "download_count", "desc", 2, 5);

        verify(materialMapper).selectList(1, 2L, "exam", "score",
                "download_count", "desc", 5, 5);
        verify(materialMapper).countList(1, 2L, "exam", "score");
    }

    @Test
    void invalidPriceFilterFallsBackToAll() {
        service().getMaterialList(null, null, null, "paid",
                null, null, 1, 10);

        verify(materialMapper).selectList(null, null, null, null,
                null, "desc", 0, 10);
        verify(materialMapper).countList(null, null, null, null);
    }

    private MaterialServiceImpl service() {
        return new MaterialServiceImpl(
                materialMapper, favoriteMapper, userMapper, scoreRecordMapper,
                materialDownloadMapper, materialReportMapper, fileStorageService);
    }

    private MaterialReportDTO report(String reason) {
        MaterialReportDTO dto = new MaterialReportDTO();
        dto.setReason(reason);
        return dto;
    }

    private Material publishedMaterial() {
        Material material = new Material();
        material.setMaterialId(6L);
        material.setStatus(1);
        material.setIsDeleted(0);
        return material;
    }
}
