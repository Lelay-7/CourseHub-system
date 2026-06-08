package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.dto.MaterialUploadDTO;
import com.bugaoshan.entity.Favorite;
import com.bugaoshan.entity.Material;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.MaterialMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.MaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    public MaterialServiceImpl(MaterialMapper materialMapper, FavoriteMapper favoriteMapper,
                               UserMapper userMapper, ScoreRecordMapper scoreRecordMapper) {
        this.materialMapper = materialMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
        this.scoreRecordMapper = scoreRecordMapper;
    }

    @Override
    public Result getCategories() {
        List<Map<String, Object>> categories = materialMapper.selectCategories();
        return Result.ok(categories);
    }

    @Override
    public Result getMaterialList(Integer materialType, Long courseId, String keyword,
                                   String sortBy, String sortOrder, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        String order = (sortOrder == null || sortOrder.isEmpty()) ? "DESC" : sortOrder.toUpperCase();

        List<Material> list = materialMapper.selectList(materialType, courseId, keyword, sortBy, order, offset, pageSize);
        long total = materialMapper.countList(materialType, courseId, keyword);

        PageResult<Material> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getMaterialDetail(Long materialId, Long userId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
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
    public Result getDownloadUrl(Long materialId, Long userId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("资料不存在");
        }

        User user = userMapper.selectById(userId);
        if (material.getDownloadScore() != null && material.getDownloadScore() > 0) {
            if (user.getScoreBalance() < material.getDownloadScore()) {
                throw new BusinessException("积分不足");
            }
            int newBalance = user.getScoreBalance() - material.getDownloadScore();
            int newTotalScore = user.getTotalScore();
            userMapper.updateScoreBalance(userId, newBalance, newTotalScore);

            ScoreRecord record = new ScoreRecord();
            record.setUserId(userId);
            record.setChangeType(2);
            record.setChangeAmount(-material.getDownloadScore());
            record.setBalanceAfter(newBalance);
            record.setSourceDesc("下载资料：" + material.getMaterialName());
            record.setRelatedId(materialId);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            scoreRecordMapper.insert(record);
        }

        materialMapper.incrementDownloadCount(materialId);

        Map<String, Object> data = new HashMap<>();
        data.put("download_url", material.getFileUrl());
        User updatedUser = userMapper.selectById(userId);
        data.put("score_balance", updatedUser.getScoreBalance());
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result uploadMaterial(Long userId, MaterialUploadDTO dto) {
        Material material = new Material();
        material.setMaterialName(dto.getMaterialName());
        material.setMaterialType(dto.getMaterialType());
        material.setCourseId(dto.getCourseId());
        material.setUploaderId(userId);
        material.setFileUrl(dto.getFileUrl());
        material.setFileSize(0L);
        material.setDownloadScore(dto.getDownloadScore() != null ? dto.getDownloadScore() : 0);
        material.setDownloadCount(0);
        material.setDescription(dto.getFileName());
        material.setKeyword(dto.getKeyword());
        material.setStatus(1);
        material.setIsDeleted(0);
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        materialMapper.insert(material);

        User user = userMapper.selectById(userId);
        int newBalance = user.getScoreBalance() + 2;
        int newTotalScore = user.getTotalScore() + 2;
        userMapper.updateScoreBalance(userId, newBalance, newTotalScore);

        ScoreRecord record = new ScoreRecord();
        record.setUserId(userId);
        record.setChangeType(1);
        record.setChangeAmount(2);
        record.setBalanceAfter(newBalance);
        record.setSourceDesc("上传资料奖励");
        record.setRelatedId(material.getMaterialId());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        scoreRecordMapper.insert(record);

        Map<String, Object> data = new HashMap<>();
        data.put("material", material);
        data.put("score_balance", newBalance);
        return Result.ok(data);
    }

    @Override
    public Result reportMaterial(Long materialId, Long userId, MaterialReportDTO dto) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("资料不存在");
        }
        return Result.ok();
    }
}
