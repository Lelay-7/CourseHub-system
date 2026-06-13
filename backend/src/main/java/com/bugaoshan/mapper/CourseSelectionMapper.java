package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.CourseSelection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseSelectionMapper extends BaseMapper<CourseSelection> {
    CourseSelection selectByUserIdAndCourseId(@Param("userId") Long userId,
                                               @Param("courseId") Long courseId);

    List<Course> selectSelectedCourses(@Param("userId") Long userId);

    int insertSelection(CourseSelection selection);

    int reactivateSelection(@Param("userId") Long userId, @Param("courseId") Long courseId);

    int dropSelected(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
