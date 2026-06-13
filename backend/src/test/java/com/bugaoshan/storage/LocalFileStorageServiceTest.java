package com.bugaoshan.storage;

import com.bugaoshan.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesFileUnderGeneratedSafeKey() throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "../notes.pdf", "application/pdf", "content".getBytes());

        StoredFile stored = service.store(file, "materials");

        assertFalse(stored.storageKey().contains(".."));
        assertTrue(Files.exists(tempDir.resolve(stored.storageKey())));
        assertTrue(service.load(stored.storageKey()).exists());
    }

    @Test
    void rejectsTraversalStorageKey() {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());

        assertThrows(BusinessException.class, () -> service.load("../outside.txt"));
    }
}
