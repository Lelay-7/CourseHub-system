package com.bugaoshan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Friend {
    @TableId(type = IdType.AUTO)
    private Long friendId;
    private Long userId;
    private Long targetId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
