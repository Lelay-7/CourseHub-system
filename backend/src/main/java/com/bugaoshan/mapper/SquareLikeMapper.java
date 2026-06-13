package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SquareLikeMapper extends BaseMapper<com.bugaoshan.entity.SquareLike> {
    int countByPostId(@Param("postId") Long postId);
    boolean existsByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
}
