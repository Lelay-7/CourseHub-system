package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.CourseReviewDTO;
import com.bugaoshan.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/course")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/categories")
    public Result<?> categories() {
        return courseService.getCategories();
    }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) Long categoryId,
                          @RequestParam(required = false) Integer courseType,
                          @RequestParam(required = false) BigDecimal creditMin,
                          @RequestParam(required = false) BigDecimal creditMax,
                          @RequestParam(required = false) String teacherName,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortOrder,
                          @RequestParam(required = false, defaultValue = "1") Integer page,
                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return courseService.getCourseList(categoryId, courseType, creditMin, creditMax, teacherName,
                keyword, sortBy, sortOrder, page, pageSize);
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false, defaultValue = "1") Integer page,
                            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return courseService.searchCourse(keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return courseService.getCourseDetail(id, userId);
    }

    @PostMapping("/{id}/review")
    public Result<?> review(@PathVariable Long id, @Valid @RequestBody CourseReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return courseService.submitReview(id, userId, dto);
    }
}
