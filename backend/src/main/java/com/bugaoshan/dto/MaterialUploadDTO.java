package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialUploadDTO {
    @NotBlank(message = "资料名称不能为空")
    @JsonProperty("material_name")
    private String materialName;

    @NotNull(message = "资料类型不能为空")
    @JsonProperty("material_type")
    private Integer materialType;

    @NotNull(message = "课程ID不能为空")
    @JsonProperty("course_id")
    private Long courseId;

    @JsonProperty("download_score")
    private Integer downloadScore;

    @JsonProperty("keyword")
    private String keyword;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("file_url")
    private String fileUrl;
}
