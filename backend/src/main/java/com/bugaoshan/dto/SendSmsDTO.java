package com.bugaoshan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendSmsDTO {
    @NotBlank(message = "手机号不能为空")
    private String phone;
}
