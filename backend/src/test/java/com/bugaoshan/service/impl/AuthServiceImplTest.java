package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.SmsCode;
import com.bugaoshan.mapper.SmsCodeMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SmsCodeMapper smsCodeMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtUtil jwtUtil;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-that-is-long-enough-for-hs256", 60_000, 120_000);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        service = new AuthServiceImpl(userMapper, smsCodeMapper, jwtUtil,
                new BCryptPasswordEncoder(), redisTemplate);
    }

    @Test
    void rejectsAccessTokenAtRefreshEndpoint() {
        String accessToken = jwtUtil.generateAccessToken(9L, 1);

        assertThrows(BusinessException.class, () -> service.refreshToken(accessToken));
    }

    @Test
    void mockSmsRejectsAnyCodeExceptConfiguredCode() {
        ReflectionTestUtils.setField(service, "smsMock", true);
        ReflectionTestUtils.setField(service, "smsMockCode", "123456");
        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "654321"));
    }

    @Test
    void mockSmsSendPersistsTheConfiguredDevelopmentCode() {
        ReflectionTestUtils.setField(service, "smsMock", true);
        ReflectionTestUtils.setField(service, "smsMockCode", "654321");

        service.sendSmsCode("13900000000");

        ArgumentCaptor<SmsCode> codeCaptor = ArgumentCaptor.forClass(SmsCode.class);
        verify(smsCodeMapper).insert(codeCaptor.capture());
        assertEquals("654321", codeCaptor.getValue().getCode());
    }

    @Test
    void smsSendRejectsRepeatedRequestWithinOneMinute() {
        when(valueOperations.setIfAbsent("sms:send:13900000000", "1", Duration.ofSeconds(60)))
                .thenReturn(false);

        assertThrows(BusinessException.class, () -> service.sendSmsCode("13900000000"));
        verify(smsCodeMapper, never()).insert(any(SmsCode.class));
    }
}
