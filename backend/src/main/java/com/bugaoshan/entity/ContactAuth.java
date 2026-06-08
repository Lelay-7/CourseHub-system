package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContactAuth {
    private Long authId;
    private Long userId;
    private Integer phoneVisible;
    private Integer emailVisible;
    private Integer contactVisible;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
