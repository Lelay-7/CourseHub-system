package com.bugaoshan.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Course {
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
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
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String teacherName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
