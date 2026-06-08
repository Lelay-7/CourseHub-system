package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.dto.*;
import com.bugaoshan.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginDTO dto) {
        return authService.login(dto.getUsername(), dto.getPassword());
    }

    @PostMapping("/login/sms")
    public Result<?> smsLogin(@Valid @RequestBody SmsLoginDTO dto) {
        return authService.smsLogin(dto.getPhone(), dto.getCode());
    }

    @PostMapping("/sms/send")
    public Result<?> sendSms(@Valid @RequestBody SendSmsDTO dto) {
        return authService.sendSmsCode(dto.getPhone());
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        return authService.register(dto);
    }

    @PostMapping("/refresh")
    public Result<?> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return authService.refreshToken(dto.getRefreshToken());
    }
}
