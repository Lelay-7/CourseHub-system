package com.bugaoshan.controller;

import com.bugaoshan.service.MaterialService;
import com.bugaoshan.storage.DownloadFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialControllerTest {

    @Mock private MaterialService materialService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void downloadStreamsAnAttachmentWithBalanceHeader() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(9L, null));
        ByteArrayResource resource = new ByteArrayResource("notes".getBytes());
        when(materialService.downloadMaterial(3L, 9L))
                .thenReturn(new DownloadFile(resource, "lecture notes.pdf", "application/pdf", 80));

        ResponseEntity<?> response = new MaterialController(materialService).download(3L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("80", response.getHeaders().getFirst("X-Score-Balance"));
        assertNotNull(response.getHeaders().getContentDisposition().getFilename());
        assertEquals(resource, response.getBody());
    }
}
