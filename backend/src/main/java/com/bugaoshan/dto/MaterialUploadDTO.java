package com.bugaoshan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MaterialUploadDTO {
    @NotBlank(message = "资料名称不能为空")
    private String materialName;

    @NotNull(message = "资料类型不能为空")
    private Integer materialType;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Integer downloadScore;

    private String description;

    private String keyword;

    private MultipartFile file;
}
