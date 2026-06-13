package com.bugaoshan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SquareComment {
    @TableId(type = IdType.AUTO)
    private Long commentId;
    private Long postId;
    private Long userId;
    private String content;
    private LocalDateTime createTime;
    private Integer isDeleted;

    @TableField(exist = false)
    private String nickname;
}
