package com.bugaoshan.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyCourseReviewDTO {
    private Long reviewId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private String teacherName;
    private Integer rating;
    private String content;
    private Integer isAnonymous;
    private LocalDateTime createTime;
}
