package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.service.MaterialService;
import com.bugaoshan.storage.DownloadFile;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/categories")
    public Result<?> categories() {
        return materialService.getCategories();
    }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) Integer materialType,
                          @RequestParam(required = false) Long courseId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String priceFilter,
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortOrder,
                          @RequestParam(required = false, defaultValue = "1") Integer page,
                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return materialService.getMaterialList(materialType, courseId, keyword, priceFilter,
                sortBy, sortOrder, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.getMaterialDetail(id, userId);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        DownloadFile download = materialService.downloadMaterial(id, userId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Score-Balance", String.valueOf(download.scoreBalance()))
                .body(download.resource());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> upload(@RequestPart("file") MultipartFile file,
                            @RequestParam String materialName,
                            @RequestParam Integer materialType,
                            @RequestParam(required = false) Long courseId,
                            @RequestParam(required = false, defaultValue = "0") Integer downloadScore,
                            @RequestParam(required = false) String keyword) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.uploadMaterial(userId, file, materialName, materialType,
                courseId, downloadScore, keyword);
    }

    @PostMapping("/{id}/report")
    public Result<?> report(@PathVariable Long id, @Valid @RequestBody MaterialReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.reportMaterial(id, userId, dto);
    }

    /** 我的上传 */
    @GetMapping("/my")
    public Result<?> myUploads() {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.getMyUploads(userId);
    }

    /** 重新提交（驳回后） */
    @PutMapping("/{id}/resubmit")
    public Result<?> resubmit(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.resubmitMaterial(id, userId);
    }

    /** 我的下载记录 */
    @GetMapping("/downloads")
    public Result<?> myDownloads(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.getMyDownloads(userId, page, pageSize);
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
