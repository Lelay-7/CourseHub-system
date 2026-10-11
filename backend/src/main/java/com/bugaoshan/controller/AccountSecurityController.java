package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.AccountSecurityDTO.*;
import com.bugaoshan.service.AccountSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountSecurityController {
    private final AccountSecurityService service;
    public AccountSecurityController(AccountSecurityService service) { this.service=service; }
    @GetMapping("/user/security")
    public Result<?> security() { return Result.ok(service.security(SecurityUtils.getCurrentUserId())); }
    @PostMapping("/user/security/email/code")
    public Result<?> bindingCode(@Valid @RequestBody BindingCode dto,HttpServletRequest request) {
        return Result.ok(service.requestBindingCode(SecurityUtils.getCurrentUserId(),dto.oldPassword(),dto.email(),request.getRemoteAddr()));
    }
    @PutMapping("/user/security/email")
    public Result<?> binding(@Valid @RequestBody Binding dto) {
        service.bindEmail(SecurityUtils.getCurrentUserId(),dto.oldPassword(),dto.email(),dto.requestId(),dto.code());
        return Result.ok();
    }
    @PutMapping("/user/security/password")
    public Result<?> password(@Valid @RequestBody Password dto) {
        service.changePassword(SecurityUtils.getCurrentUserId(),dto.oldPassword(),dto.newPassword()); return Result.ok();
    }
    @PostMapping("/auth/password-reset/code")
    public Result<?> resetCode(@Valid @RequestBody ResetCode dto,HttpServletRequest request) {
        // Forwarded headers are untrusted unless the deployment explicitly configures a trusted proxy.
        return Result.ok(service.requestResetCode(dto.username(),dto.email(),request.getRemoteAddr()));
    }
    @PostMapping("/auth/password-reset")
    public Result<?> reset(@Valid @RequestBody Reset dto) {
        service.resetPassword(dto.username(),dto.email(),dto.requestId(),dto.code(),dto.newPassword()); return Result.ok();
    }
}
