package com.bugaoshan.controller;

import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.AuthService;
import com.bugaoshan.storage.FileStorageService;
import com.bugaoshan.storage.StoredFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private AuthService authService;
    @Mock private UserMapper userMapper;
    @Mock private FileStorageService fileStorageService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void avatarUploadPersistsStorageKeyAndReturnsResourceUrl() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(12L, null));
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        when(fileStorageService.store(file, "avatars"))
                .thenReturn(new StoredFile("avatars/2026/06/id.png", "avatar.png", "image/png", 3));

        var result = new UserController(authService, userMapper, fileStorageService).uploadAvatar(file);

        verify(userMapper).updateAvatar(12L, "/api/user/12/avatar", "avatars/2026/06/id.png");
        assertEquals("/api/user/12/avatar", ((Map<String, Object>) result.getData()).get("avatar_url"));
    }
}
