package com.bugaoshan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Feedback {
    private Long feedbackId;
    private Long userId;
    private String type;
    private String content;
    private String contact;
    private Integer status;
    private String reply;
    private LocalDateTime replyTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String nickname;
}
