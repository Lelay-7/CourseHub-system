package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScoreRecord {
    private Long recordId;
    private Long userId;
    private Integer changeType;
    private Integer changeAmount;
    private Integer balanceAfter;
    private String sourceDesc;
    private Long relatedId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
