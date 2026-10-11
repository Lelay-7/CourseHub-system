package com.bugaoshan.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ChatSendRateWindow {
    private Long userId;
    private LocalDateTime windowStart,expiresAt;
    private Integer windowSeconds,usedCount;
}
