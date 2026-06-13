package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseReview {
    private Long reviewId;
    private Long courseId;
    private Long userId;
    private Integer rating;
    private String content;
    private Integer isAnonymous;
    private Integer rewardGranted;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
