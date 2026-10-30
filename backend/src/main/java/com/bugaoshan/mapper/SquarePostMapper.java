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
                                       @Param("tag") String tag,
                                       @Param("courseId") Long courseId,
                                       @Param("offset") Integer offset,
                                       @Param("limit") Integer limit);

    default List<SquarePost> selectAllWithUser(Integer status, String keyword, String tag, Integer offset, Integer limit) {
        return selectAllWithUser(status, keyword, tag, null, offset, limit);
    }

    long countAllWithUser(@Param("status") Integer status,
                          @Param("keyword") String keyword,
                          @Param("tag") String tag, @Param("courseId") Long courseId);

    default long countAllWithUser(Integer status, String keyword, String tag) {
        return countAllWithUser(status, keyword, tag, null);
    }

    int deleteById(@Param("postId") Long postId);
}
