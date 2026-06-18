package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.SquarePost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SquarePostMapper extends BaseMapper<SquarePost> {

    List<SquarePost> selectAllWithUser(@Param("status") Integer status,
                                       @Param("keyword") String keyword,
                                       @Param("offset") Integer offset,
                                       @Param("limit") Integer limit);

    long countAllWithUser(@Param("status") Integer status,
                          @Param("keyword") String keyword);

    int deleteById(@Param("postId") Long postId);
}
