package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.mapper.MaterialReportMapper;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final MaterialReportMapper reports;
    public ReportController(MaterialReportMapper reports) { this.reports = reports; }

    @GetMapping("/my")
    public Result<?> mine(@RequestParam(defaultValue="1") int page,
                          @RequestParam(defaultValue="20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = (Math.max(page, 1) - 1) * size;
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(Map.of("list", reports.selectByReporter(userId, offset, size),
                "total", reports.countByReporter(userId)));
    }
}
