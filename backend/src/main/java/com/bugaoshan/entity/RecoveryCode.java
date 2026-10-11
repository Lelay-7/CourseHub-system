package com.bugaoshan.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RecoveryCode {
    private String requestId;
    private Long userId;
    private String purpose;
    private String email;
    private Integer credentialVersion;
    private String codeHash;
    private LocalDateTime expiresAt;
    private String sendStatus;
    private Integer attempts;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
}
