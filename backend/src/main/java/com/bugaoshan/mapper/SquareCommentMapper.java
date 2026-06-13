package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.SquareComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SquareCommentMapper extends BaseMapper<SquareComment> {
    List<SquareComment> selectByPostId(@Param("postId") Long postId);
    int countByPostId(@Param("postId") Long postId);
    int deleteByIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);
    int softDeleteById(@Param("commentId") Long commentId);
}
