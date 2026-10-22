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

    @GetMapping("/teachers")
    public Result<?> teachers() { return courseService.getTeachers(); }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) Long categoryId,
                          @RequestParam(required = false) Integer courseType,
                          @RequestParam(required = false) BigDecimal creditMin,
                          @RequestParam(required = false) BigDecimal creditMax,
                          @RequestParam(required = false) String teacherName,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String timeSlot,
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortOrder,
                          @RequestParam(required = false, defaultValue = "1") Integer page,
                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return courseService.getCourseList(categoryId, courseType, creditMin, creditMax, teacherName,
                keyword, timeSlot, sortBy, sortOrder, page, pageSize);
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false, defaultValue = "1") Integer page,
                            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return courseService.searchCourse(keyword, page, pageSize);
    }

    @GetMapping("/selected")
    public Result<?> selected() {
        return courseService.getSelectedCourses(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/reviews/my")
    public Result<?> myReviews() {
        return courseService.getMyReviews(SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/{id}/select")
    public Result<?> select(@PathVariable Long id) {
        return courseService.selectCourse(id, SecurityUtils.getCurrentUserId());
    }

    @DeleteMapping("/{id}/select")
    public Result<?> drop(@PathVariable Long id) {
        return courseService.dropCourse(id, SecurityUtils.getCurrentUserId());
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

    @DeleteMapping("/{id}/review")
    public Result<?> deleteReview(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return courseService.deleteReview(id, userId);
    }
}
