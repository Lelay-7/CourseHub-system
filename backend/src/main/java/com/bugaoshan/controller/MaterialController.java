package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.MaterialReportDTO;
import com.bugaoshan.dto.MaterialUploadDTO;
import com.bugaoshan.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortOrder,
                          @RequestParam(required = false, defaultValue = "1") Integer page,
                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return materialService.getMaterialList(materialType, courseId, keyword,
                sortBy, sortOrder, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.getMaterialDetail(id, userId);
    }

    @GetMapping("/{id}/download")
    public Result<?> download(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.getDownloadUrl(id, userId);
    }

    @PostMapping("/upload")
    public Result<?> upload(@Valid @RequestBody MaterialUploadDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.uploadMaterial(userId, dto);
    }

    @PostMapping("/{id}/report")
    public Result<?> report(@PathVariable Long id, @Valid @RequestBody MaterialReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return materialService.reportMaterial(id, userId, dto);
    }
}
