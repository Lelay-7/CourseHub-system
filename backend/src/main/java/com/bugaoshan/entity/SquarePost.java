package com.bugaoshan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SquarePost {
    @TableId(type = IdType.AUTO)
    private Long postId;
    private Long userId;
    private String content;
    private String courseName;
    private String materialName;
    private String tags;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;

    @TableField(exist = false)
    private String nickname;

    @TableField(exist = false)
    private String avatarUrl;
}
