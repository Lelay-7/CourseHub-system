package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.dto.CourseReviewWithUser;
import com.bugaoshan.dto.MyCourseReviewDTO;
import com.bugaoshan.entity.CourseReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CourseReviewMapper extends BaseMapper<CourseReview> {

    CourseReview selectById(@Param("reviewId") Long reviewId);

    List<CourseReview> selectByCourseId(@Param("courseId") Long courseId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    List<CourseReview> selectAllForAdmin(@Param("offset") Integer offset, @Param("limit") Integer limit);

    int insert(CourseReview review);

    int countByCourseId(@Param("courseId") Long courseId);

    boolean existsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    int deleteById(@Param("reviewId") Long reviewId);

    CourseReview selectByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    int reactivateReview(@Param("reviewId") Long reviewId, @Param("rating") Integer rating,
                         @Param("content") String content, @Param("isAnonymous") Integer isAnonymous,
                         @Param("updateTime") java.time.LocalDateTime updateTime);

    int claimReviewReward(@Param("reviewId") Long reviewId);

    int deleteByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    List<CourseReviewWithUser> selectWithUserByCourseId(@Param("courseId") Long courseId,
                                                         @Param("offset") Integer offset,
                                                         @Param("limit") Integer limit);

    Map<String, Object> calcAvgAndCount(@Param("courseId") Long courseId);

    List<MyCourseReviewDTO> selectMyReviews(@Param("userId") Long userId);
}
