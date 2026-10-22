package com.bugaoshan.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MaterialAuditLog {
    private Long auditId;
    private Long materialId;
    private Integer revision;
    private Long reviewerId;
    private String reviewerName;
    private String action;
    private String reason;
    private LocalDateTime createTime;
}
