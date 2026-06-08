package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long userId;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String email;
    private Integer userType;
    private String realName;
    private String studentNo;
    private String department;
    private String className;
    private String title;
    private String officeLocation;
    private String officePhone;
    private Integer scoreBalance;
    private Integer totalScore;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
