package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Favorite {
    private Long favoriteId;
    private Long userId;
    private Integer targetType;
    private Long targetId;
    private LocalDateTime createTime;
}
