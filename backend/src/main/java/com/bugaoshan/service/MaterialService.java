package com.bugaoshan.service;

import com.bugaoshan.common.Result;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.storage.DownloadFile;
import org.springframework.web.multipart.MultipartFile;

public interface MaterialService {
    Result getCategories();
    Result getMaterialList(Integer materialType, Long courseId, String keyword, String priceFilter,
                           String sortBy, String sortOrder, Integer page, Integer pageSize);
    Result getMaterialDetail(Long materialId, Long userId);
    DownloadFile downloadMaterial(Long materialId, Long userId);
    Result uploadMaterial(Long userId, MultipartFile file, String materialName, Integer materialType,
                          Long courseId, Integer downloadScore, String keyword, String description);
    Result reportMaterial(Long materialId, Long userId, MaterialReportDTO dto);
    Result getMyUploads(Long userId);
    Result resubmitMaterial(Long materialId, Long userId, Integer expectedRevision, MultipartFile file,
                            String materialName, Integer materialType, Long courseId,
                            Integer downloadScore, String keyword, String description);
    Result getMyDownloads(Long userId, Integer page, Integer pageSize);
}
