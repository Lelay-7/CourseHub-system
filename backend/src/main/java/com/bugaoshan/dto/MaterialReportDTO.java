package com.bugaoshan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MaterialReportDTO {
    @NotBlank(message = "举报原因不能为空")
    private String reason;
}
