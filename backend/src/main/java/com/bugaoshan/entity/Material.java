package com.bugaoshan.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class Material {
    private Long materialId;
    private String materialName;
    private Integer materialType;
    private Long courseId;
    private Long uploaderId;
    @JsonIgnore
    private String fileUrl;
    @JsonIgnore
    private String storageKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private Integer downloadScore;
    private Integer downloadCount;
    private String description;
    private String rejectReason;
    private Integer status;
    private Integer revision;
    @JsonIgnore
    private Integer uploadRewarded;
    @TableField(exist = false)
    private String uploaderName;
    @TableField(exist = false)
    private Boolean isFavorited;
    private String keyword;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;

    public boolean getHasFile() {
        return storageKey != null && !storageKey.isBlank();
    }
}
