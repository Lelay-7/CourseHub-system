package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.MaterialReviewRequest;
import com.bugaoshan.service.MaterialReviewService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/materials")
public class AdminMaterialController {
    private final MaterialReviewService review;
    public AdminMaterialController(MaterialReviewService review) { this.review=review; }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) { return Result.ok(review.detail(id,SecurityUtils.getCurrentUserId())); }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id,@RequestParam(required=false) Integer revision) {
        var download=review.file(id,revision,SecurityUtils.getCurrentUserId());
        MediaType type;
        try { type=MediaType.parseMediaType(download.contentType()); } catch(IllegalArgumentException ex) { type=MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(download.resource());
    }
    @PutMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id,@Valid @RequestBody MaterialReviewRequest request) {
        review.approve(id,request.expectedRevision(),SecurityUtils.getCurrentUserId()); return Result.ok();
    }
    @PutMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id,@Valid @RequestBody MaterialReviewRequest request) {
        review.reject(id,request.expectedRevision(),SecurityUtils.getCurrentUserId(),request.reason()); return Result.ok();
    }
}
