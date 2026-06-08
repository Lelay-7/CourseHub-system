package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.AvatarUploadDTO;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;
    private final UserMapper userMapper;

    public UserController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @GetMapping("/auth/userinfo")
    public Result<?> userinfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        return authService.getUserInfo(userId);
    }

    @PostMapping("/user/avatar")
    public Result<?> uploadAvatar(@Valid @RequestBody AvatarUploadDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        userMapper.updateAvatar(userId, dto.getAvatarUrl());
        return Result.ok();
    }
}
