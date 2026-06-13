package com.bugaoshan.storage;

import org.springframework.core.io.Resource;

public record DownloadFile(
        Resource resource,
        String filename,
        String contentType,
        Integer scoreBalance
) {
}
