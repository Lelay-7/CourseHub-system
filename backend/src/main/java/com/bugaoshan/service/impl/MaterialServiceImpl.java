package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.entity.Favorite;
import com.bugaoshan.entity.Material;
import com.bugaoshan.entity.MaterialReport;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.MaterialMapper;
import com.bugaoshan.mapper.MaterialDownloadMapper;
import com.bugaoshan.mapper.MaterialReportMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.MaterialService;
import com.bugaoshan.storage.DownloadFile;
import com.bugaoshan.storage.FileStorageService;
import com.bugaoshan.storage.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;
    private final MaterialDownloadMapper materialDownloadMapper;
    private final MaterialReportMapper materialReportMapper;
    private final FileStorageService fileStorageService;

    public MaterialServiceImpl(MaterialMapper materialMapper, FavoriteMapper favoriteMapper,
                               UserMapper userMapper, ScoreRecordMapper scoreRecordMapper,
                               MaterialDownloadMapper materialDownloadMapper,
                               MaterialReportMapper materialReportMapper,
                               FileStorageService fileStorageService) {
        this.materialMapper = materialMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
        this.scoreRecordMapper = scoreRecordMapper;
        this.materialDownloadMapper = materialDownloadMapper;
        this.materialReportMapper = materialReportMapper;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Result getCategories() {
        List<Map<String, Object>> categories = materialMapper.selectCategories();
        return Result.ok(categories);
    }

    @Override
    public Result getMaterialList(Integer materialType, Long courseId, String keyword,
                                   String priceFilter, String sortBy, String sortOrder,
                                   Integer page, Integer pageSize) {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 10;
        int offset = (page - 1) * pageSize;
        String order = (sortOrder == null || sortOrder.isEmpty()) ? "desc" : sortOrder.toLowerCase();
        String normalizedPriceFilter = "free".equals(priceFilter) || "score".equals(priceFilter)
                ? priceFilter : null;

        List<Material> list = materialMapper.selectList(materialType, courseId, keyword,
                normalizedPriceFilter, sortBy, order, offset, pageSize);
        long total = materialMapper.countList(materialType, courseId, keyword, normalizedPriceFilter);

        PageResult<Material> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getMaterialDetail(Long materialId, Long userId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("资料不存在");
        }
        // 非本人且未审核通过的资料不可见
        if (material.getStatus() != 1 && (userId == null || !material.getUploaderId().equals(userId))) {
            throw new BusinessException("资料不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("material", material);

        if (userId != null) {
            Favorite fav = favoriteMapper.selectByUserIdAndTarget(userId, 2, materialId);
            data.put("is_favorited", fav != null);
        } else {
            data.put("is_favorited", false);
        }

        return Result.ok(data);
    }

    @Override
    @Transactional
    public DownloadFile downloadMaterial(Long materialId, Long userId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("资料不存在");
        }
        if (material.getStatus() == null || (material.getStatus() != 1
                && !userId.equals(material.getUploaderId()))) {
            throw new BusinessException("资料不存在或尚未通过审核");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户不存在或已被禁用");
        }
        int cost = material.getDownloadScore() != null ? material.getDownloadScore() : 0;
        boolean firstDownload = materialDownloadMapper.insertIgnore(materialId, userId, cost) == 1;
        int newBalance = user.getScoreBalance();
        if (firstDownload && cost > 0) {
            if (userMapper.deductScoreIfSufficient(userId, cost) != 1) {
                throw new BusinessException("积分不足");
            }
            user = userMapper.selectById(userId);
            newBalance = user.getScoreBalance();
            ScoreRecord record = new ScoreRecord();
            record.setUserId(userId);
            record.setChangeType(2);
            record.setChangeAmount(-cost);
            record.setBalanceAfter(newBalance);
            record.setSourceDesc("下载资料：" + material.getMaterialName());
            record.setRelatedId(materialId);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            scoreRecordMapper.insert(record);
        }

        materialMapper.incrementDownloadCount(materialId);
        if (material.getStorageKey() == null || material.getStorageKey().isBlank()) {
            throw new BusinessException("资料文件不存在");
        }
        return new DownloadFile(fileStorageService.load(material.getStorageKey()),
                material.getOriginalFileName() != null ? material.getOriginalFileName() : material.getMaterialName(),
                material.getContentType() != null ? material.getContentType() : "application/octet-stream",
                newBalance);
    }

    @Override
    @Transactional
    public Result uploadMaterial(Long userId, MultipartFile file, String materialName, Integer materialType,
                                 Long courseId, Integer downloadScore, String keyword) {
        validateUpload(file, materialName, materialType, courseId, downloadScore);
        StoredFile stored = fileStorageService.store(file, "materials");
        Material material = new Material();
        material.setFileUrl("/uploads/materials/" + stored.storageKey());
        material.setMaterialName(materialName.trim());
        material.setMaterialType(materialType);
        material.setCourseId(courseId);
        material.setUploaderId(userId);
        material.setStorageKey(stored.storageKey());
        material.setOriginalFileName(stored.originalFilename());
        material.setContentType(stored.contentType());
        material.setFileSize(stored.size());
        material.setDownloadScore(downloadScore != null ? downloadScore : 0);
        material.setDownloadCount(0);
        material.setDescription(stored.originalFilename());
        material.setKeyword(keyword);
        material.setStatus(0); // 待审核
        material.setIsDeleted(0);
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        materialMapper.insert(material);

        Map<String, Object> data = new HashMap<>();
        data.put("material", material);
        data.put("score_balance", userMapper.selectById(userId).getScoreBalance());
        return Result.ok(data);
    }

    private void validateUpload(MultipartFile file, String materialName, Integer materialType,
                                Long courseId, Integer downloadScore) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择上传文件");
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException("文件不能超过50MB");
        if (materialName == null || materialName.isBlank()) throw new BusinessException("资料名称不能为空");
        if (materialType == null) throw new BusinessException("资料类型不能为空");
        if (downloadScore != null && (downloadScore < 0 || downloadScore > 1000)) {
            throw new BusinessException("下载积分范围无效");
        }
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("application/") || contentType.startsWith("image/")
                || contentType.equals("text/plain"))) {
            throw new BusinessException("不支持的文件类型");
        }
    }

    @Override
    @Transactional
    public Result reportMaterial(Long materialId, Long userId, MaterialReportDTO dto) {
        Material material = materialMapper.selectById(materialId);
        if (material == null || !Integer.valueOf(1).equals(material.getStatus())
                || Integer.valueOf(1).equals(material.getIsDeleted())) {
            throw new BusinessException("资料不存在或未发布");
        }
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new BusinessException("举报原因不能为空");
        }
        String reason = dto.getReason().trim();
        if (reason.length() > 500) {
            throw new BusinessException("举报原因不能超过 500 字");
        }
        if (materialReportMapper.existsPending(materialId, userId)) {
            throw new BusinessException("你已举报过该内容，管理员正在处理");
        }
        MaterialReport report = new MaterialReport();
        report.setMaterialId(materialId);
        report.setReporterId(userId);
        report.setReason(reason);
        report.setStatus(0);
        report.setCreateTime(LocalDateTime.now());
        materialReportMapper.insert(report);
        return Result.ok(Map.of("report_id", report.getReportId()));
    }

    @Override
    public Result getMyUploads(Long userId) {
        List<Material> list = materialMapper.selectByUploaderId(userId);
        return Result.ok(list);
    }

    @Override
    public Result resubmitMaterial(Long materialId, Long userId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) throw new BusinessException("资料不存在");
        if (!material.getUploaderId().equals(userId)) throw new BusinessException("无权操作");
        if (material.getStatus() != 2) throw new BusinessException("仅可重新提交已驳回的资料");
        if (materialMapper.resubmitRejected(materialId) != 1) {
            throw new BusinessException("资料状态已变化，请刷新后重试");
        }
        return Result.ok();
    }

    @Override
    public Result getMyDownloads(Long userId, Integer page, Integer pageSize) {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 20;
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> list = materialDownloadMapper.selectByUserId(userId, offset, pageSize);
        long total = materialDownloadMapper.countByUserId(userId);
        return Result.ok(new PageResult<>(list, total));
    }
}
