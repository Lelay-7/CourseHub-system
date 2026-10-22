package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialReport {
    private Long reportId;
    private Long materialId;
    private Long reporterId;
    private String reason;
    private Integer status;
    private String materialName;
    private String resolutionAction;
    private String resolutionReason;
    private Long handledBy;
    private LocalDateTime handledTime;
    private LocalDateTime createTime;
}
