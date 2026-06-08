package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SmsCode {
    private Long smsId;
    private String phone;
    private String code;
    private Integer type;
    private LocalDateTime expireTime;
    private Integer isUsed;
    private LocalDateTime createTime;
}
