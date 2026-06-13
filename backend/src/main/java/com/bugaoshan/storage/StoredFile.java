package com.bugaoshan.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long size
) {
}
