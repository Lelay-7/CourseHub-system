package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Material {
    private Long materialId;
    private String materialName;
    private Integer materialType;
    private Long courseId;
    private Long uploaderId;
    private String fileUrl;
    private Long fileSize;
    private Integer downloadScore;
    private Integer downloadCount;
    private String description;
    private Integer status;
    private String keyword;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
