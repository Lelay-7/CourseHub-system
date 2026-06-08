package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AvatarUploadDTO {
    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("file_name")
    private String fileName;
}
