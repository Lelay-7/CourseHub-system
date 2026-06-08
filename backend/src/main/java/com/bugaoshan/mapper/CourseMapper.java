package com.bugaoshan.mapper;

import com.bugaoshan.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface CourseMapper {

    List<Course> selectList(@Param("categoryId") Long categoryId,
                            @Param("courseType") Integer courseType,
                            @Param("creditMin") BigDecimal creditMin,
                            @Param("creditMax") BigDecimal creditMax,
                            @Param("teacherName") String teacherName,
                            @Param("keyword") String keyword,
                            @Param("sortBy") String sortBy,
                            @Param("sortOrder") String sortOrder,
                            @Param("offset") Integer offset,
                            @Param("limit") Integer limit);

    long countList(@Param("categoryId") Long categoryId,
                   @Param("courseType") Integer courseType,
                   @Param("creditMin") BigDecimal creditMin,
                   @Param("creditMax") BigDecimal creditMax,
                   @Param("teacherName") String teacherName,
                   @Param("keyword") String keyword);

    Course selectById(@Param("courseId") Long courseId);

    List<Course> selectRelatedCourses(@Param("courseId") Long courseId, @Param("limit") Integer limit);

    int updateRating(@Param("courseId") Long courseId, @Param("avgRating") BigDecimal avgRating, @Param("ratingCount") Integer ratingCount);

    int incrementSelectionCount(@Param("courseId") Long courseId);

    List<Map<String, Object>> selectCategories();
}
