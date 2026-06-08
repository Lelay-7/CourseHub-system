package com.bugaoshan.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseReviewWithUser {
    private Long reviewId;
    private Long courseId;
    private Long userId;
    private String userName;
    private String avatarUrl;
    private Integer rating;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
