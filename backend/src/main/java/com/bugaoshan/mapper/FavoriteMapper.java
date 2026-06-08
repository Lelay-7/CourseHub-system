package com.bugaoshan.mapper;

import com.bugaoshan.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    List<Favorite> selectByUserIdAndType(@Param("userId") Long userId, @Param("targetType") Integer targetType);

    Favorite selectByUserIdAndTarget(@Param("userId") Long userId, @Param("targetType") Integer targetType, @Param("targetId") Long targetId);

    int insert(Favorite favorite);

    int deleteByUserIdAndTarget(@Param("userId") Long userId, @Param("targetType") Integer targetType, @Param("targetId") Long targetId);
}
