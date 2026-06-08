package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.dto.FavoriteDTO;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.Favorite;
import com.bugaoshan.entity.Material;
import com.bugaoshan.mapper.CourseMapper;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.MaterialMapper;
import com.bugaoshan.service.FavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final CourseMapper courseMapper;
    private final MaterialMapper materialMapper;

    public FavoriteServiceImpl(FavoriteMapper favoriteMapper,
                               CourseMapper courseMapper,
                               MaterialMapper materialMapper) {
        this.favoriteMapper = favoriteMapper;
        this.courseMapper = courseMapper;
        this.materialMapper = materialMapper;
    }

    @Override
    public Result getFavoriteList(Long userId, String type) {
        int targetType = "course".equals(type) ? 1 : 2;
        List<Favorite> favorites = favoriteMapper.selectByUserIdAndType(userId, targetType);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Favorite fav : favorites) {
            Map<String, Object> item = new HashMap<>();
            item.put("favoriteId", fav.getFavoriteId());
            item.put("targetType", fav.getTargetType());
            item.put("targetId", fav.getTargetId());
            item.put("createTime", fav.getCreateTime());

            if (targetType == 1) {
                Course course = courseMapper.selectById(fav.getTargetId());
                if (course != null) {
                    item.put("courseName", course.getCourseName());
                    item.put("courseCode", course.getCourseCode());
                    item.put("credit", course.getCredit());
                    item.put("avgRating", course.getAvgRating());
                    item.put("coverUrl", course.getCoverUrl());
                }
            } else {
                Material material = materialMapper.selectById(fav.getTargetId());
                if (material != null) {
                    item.put("materialName", material.getMaterialName());
                    item.put("materialType", material.getMaterialType());
                    item.put("fileUrl", material.getFileUrl());
                    item.put("downloadCount", material.getDownloadCount());
                    item.put("description", material.getDescription());
                }
            }
            list.add(item);
        }

        return Result.ok(list);
    }

    @Override
    @Transactional
    public Result addFavorite(Long userId, FavoriteDTO dto) {
        int targetType = "course".equals(dto.getType()) ? 1 : 2;
        Favorite existing = favoriteMapper.selectByUserIdAndTarget(userId, targetType, dto.getTargetId());
        if (existing != null) {
            throw new BusinessException("已收藏，请勿重复操作");
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(dto.getTargetId());
        favorite.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(favorite);

        return Result.ok();
    }

    @Override
    public Result removeFavorite(Long userId, FavoriteDTO dto) {
        int targetType = "course".equals(dto.getType()) ? 1 : 2;
        Favorite existing = favoriteMapper.selectByUserIdAndTarget(userId, targetType, dto.getTargetId());
        if (existing == null) {
            throw new BusinessException("收藏不存在");
        }
        favoriteMapper.deleteByUserIdAndTarget(userId, targetType, dto.getTargetId());
        return Result.ok();
    }
}
