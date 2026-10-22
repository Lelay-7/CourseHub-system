package com.bugaoshan.storage;

import com.bugaoshan.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test void failedStreamIsClosedAndItsPartialFileIsRemoved() throws Exception {
        var closed=new AtomicBoolean();
        var file=new MockMultipartFile("file","broken.pdf","application/pdf",new byte[]{1,2}) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    int reads;
                    @Override public int read() throws IOException {
                        if(reads++==0) return 1;
                        throw new IOException("connection interrupted");
                    }
                    @Override public void close() { closed.set(true); }
                };
            }
        };
        var service=new LocalFileStorageService(tempDir.toString());
        assertThrows(BusinessException.class,()->service.store(file,"materials"));
        try(var paths=Files.walk(tempDir)) {
            long remaining=paths.filter(Files::isRegularFile).count();
            assertAll(()->assertTrue(closed.get(),"Failed uploads must release the multipart input stream"),
                    ()->assertEquals(0,remaining,"No partial upload should remain"));
        }
    }
}
