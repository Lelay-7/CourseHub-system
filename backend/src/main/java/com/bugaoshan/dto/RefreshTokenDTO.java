package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenDTO {
    @NotBlank(message = "refreshToken不能为空")
    @JsonProperty("refreshToken")
    private String refreshToken;
}
