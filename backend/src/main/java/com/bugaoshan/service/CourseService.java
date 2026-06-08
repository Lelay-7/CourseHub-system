package com.bugaoshan.service;

import com.bugaoshan.common.Result;
import com.bugaoshan.dto.CourseReviewDTO;

import java.math.BigDecimal;

public interface CourseService {
    Result getCategories();
    Result getCourseList(Long categoryId, Integer courseType, BigDecimal creditMin, BigDecimal creditMax,
                         String teacherName, String keyword, String sortBy, String sortOrder,
                         Integer page, Integer pageSize);
    Result getCourseDetail(Long courseId, Long userId);
    Result searchCourse(String keyword, Integer page, Integer pageSize);
    Result submitReview(Long courseId, Long userId, CourseReviewDTO dto);
}
