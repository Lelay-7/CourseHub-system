package com.bugaoshan.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Course {
    private Long courseId;
    private String courseCode;
    private String courseName;
    private Integer courseType;
    private BigDecimal credit;
    private Long teacherId;
    private String semester;
    private String schedule;
    private String location;
    private String coverUrl;
    private String outline;
    private Integer selectionCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private String description;
    private Integer status;
    private Long categoryId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
