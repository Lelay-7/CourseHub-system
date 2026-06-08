package com.bugaoshan.service;

import com.bugaoshan.common.Result;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.dto.MaterialUploadDTO;
import org.springframework.web.multipart.MultipartFile;

public interface MaterialService {
    Result getCategories();
    Result getMaterialList(Integer materialType, Long courseId, String keyword, String sortBy, String sortOrder,
                           Integer page, Integer pageSize);
    Result getMaterialDetail(Long materialId, Long userId);
    Result getDownloadUrl(Long materialId, Long userId);
    Result uploadMaterial(Long userId, MaterialUploadDTO dto, MultipartFile file);
    Result reportMaterial(Long materialId, Long userId, MaterialReportDTO dto);
}
