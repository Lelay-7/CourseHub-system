package com.bugaoshan.service;

import com.bugaoshan.common.Result;
import com.bugaoshan.dto.RegisterDTO;

public interface AuthService {
    Result login(String username, String password);
    Result smsLogin(String phone, String code);
    Result sendSmsCode(String phone);
    Result register(RegisterDTO dto);
    Result refreshToken(String refreshToken);
    Result getUserInfo(Long userId);
}
