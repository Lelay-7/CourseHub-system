package com.bugaoshan.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile store(MultipartFile file, String category);

    Resource load(String storageKey);

    void delete(String storageKey);
}
