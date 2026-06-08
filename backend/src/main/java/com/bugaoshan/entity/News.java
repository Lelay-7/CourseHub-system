package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class News {
    private Long newsId;
    private String title;
    private String summary;
    private String content;
    private Long categoryId;
    private String coverUrl;
    private Integer viewCount;
    private Integer status;
    private LocalDateTime publishTime;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
