package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Banner {
    private Long bannerId;
    private String title;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
