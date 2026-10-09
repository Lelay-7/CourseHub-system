package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.entity.User;
import com.bugaoshan.dto.ProfileUpdateDTO;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.AuthService;
import com.bugaoshan.storage.FileStorageService;
import com.bugaoshan.storage.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DuplicateKeyException;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    public UserController(AuthService authService, UserMapper userMapper,
                          FileStorageService fileStorageService) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/auth/userinfo")
    public Result<?> userinfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        return authService.getUserInfo(userId);
    }

    @PostMapping(value = "/user/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadAvatar(@RequestPart("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        validateAvatar(file);
        StoredFile stored = fileStorageService.store(file, "avatars");
        String avatarUrl = "/api/user/" + userId + "/avatar";
        userMapper.updateAvatar(userId, avatarUrl, stored.storageKey());
        Map<String, Object> data = new HashMap<>();
        data.put("avatar_url", avatarUrl);
        return Result.ok(data);
    }

    @GetMapping("/user/{userId}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getAvatarStorageKey() == null || user.getAvatarStorageKey().isBlank()) {
            throw new BusinessException("头像不存在");
        }
        Resource resource = fileStorageService.load(user.getAvatarStorageKey());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @PutMapping("/user/profile")
    public Result<?> updateProfile(@Valid @RequestBody ProfileUpdateDTO body) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (body.hasField("phone") && body.getPhone() != null) {
            User owner = userMapper.selectByPhone(body.getPhone());
            if (owner != null && !Objects.equals(owner.getUserId(), userId)) {
                throw new BusinessException(409, "手机号已被其他账号使用");
            }
        }
        if (body.hasField("nickname")) user.setNickname(body.getNickname());
        if (body.hasField("phone")) user.setPhone(body.getPhone());
        if (body.hasField("email")) user.setEmail(body.getEmail());
        if (body.hasField("department")) user.setDepartment(body.getDepartment());
        if (body.hasField("className")) user.setClassName(body.getClassName());
        if (body.hasField("studentNo")) user.setStudentNo(body.getStudentNo());
        if (body.hasField("title")) user.setTitle(body.getTitle());
        if (body.hasField("officeLocation")) user.setOfficeLocation(body.getOfficeLocation());
        user.setUpdateTime(java.time.LocalDateTime.now());
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "手机号已被其他账号使用");
        }
        return Result.ok();
    }

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        if (file.getSize() > 2L * 1024 * 1024) {
            throw new BusinessException("头像不能超过2MB");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException("头像必须是图片文件");
        }
    }
}
