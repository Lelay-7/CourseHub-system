package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteDTO {
    @NotBlank(message = "目标类型不能为空")
    @JsonProperty("type")
    private String type;

    @NotNull(message = "目标ID不能为空")
    @JsonProperty("target_id")
    private Long targetId;
}
