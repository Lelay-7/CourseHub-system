package com.bugaoshan.mapper;

import com.bugaoshan.entity.CourseReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseReviewMapper {

    List<CourseReview> selectByCourseId(@Param("courseId") Long courseId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    int insert(CourseReview review);

    int countByCourseId(@Param("courseId") Long courseId);

    boolean existsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
