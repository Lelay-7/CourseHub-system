package com.bugaoshan.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SquareLike {
    private Long likeId;
    private Long postId;
    private Long userId;
    private LocalDateTime createTime;
}
