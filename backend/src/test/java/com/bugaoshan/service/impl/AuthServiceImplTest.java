package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.SmsCode;
import com.bugaoshan.entity.User;
import com.bugaoshan.dto.RegisterDTO;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import org.springframework.dao.DuplicateKeyException;

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
        enableMockSms();
        when(valueOperations.setIfAbsent("sms:send:13900000000", "1", Duration.ofSeconds(60)))
                .thenReturn(false);

        assertThrows(BusinessException.class, () -> service.sendSmsCode("13900000000"));
        verify(smsCodeMapper, never()).insert(any(SmsCode.class));
    }

    @Test
    void rejectsLegacyRefreshTokenWithoutCredentialVersion() {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "test-secret-key-that-is-long-enough-for-hs256".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String legacy = io.jsonwebtoken.Jwts.builder().subject("12").claim("type", "refresh")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60000)).signWith(key).compact();
        lenient().when(userMapper.selectById(12L)).thenReturn(activeUser());
        assertThrows(BusinessException.class, () -> service.refreshToken(legacy));
    }

    @Test
    void oldRefreshVersionCannotIssueNewTokensButCurrentVersionCan() {
        User user=activeUser(); user.setCredentialVersion(3);
        when(userMapper.selectById(12L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> service.refreshToken(jwtUtil.generateRefreshToken(12L,2)));
        var result=service.refreshToken(jwtUtil.generateRefreshToken(12L,3));
        var data=(java.util.Map<?,?>)result.getData();
        assertTrue(jwtUtil.matchesCredentialVersion((String)data.get("accessToken"),3));
        assertTrue(jwtUtil.matchesCredentialVersion((String)data.get("refreshToken"),3));
    }

    @Test
    void passwordLoginTrimsTheAccountWithoutChangingItsIdentity() {
        User user = activeUser();
        when(userMapper.selectByUsername("student01")).thenReturn(user);

        assertDoesNotThrow(() -> service.login("  student01  ", "123456"));
    }

    @Test
    void passwordLoginRejectsMalformedAccountBeforeLookup() {
        var error = assertThrows(BusinessException.class, () -> service.login("  ", "123456"));

        assertTrue(error.getMessage().contains("账号"));
        verify(userMapper, never()).selectByUsername(anyString());
    }

    @Test
    void registrationStoresNormalizedAccountAndNickname() {
        RegisterDTO dto = registration("  student01  ");
        dto.setNickname("  同学甲  ");
        doAnswer(invocation -> {
            ((User) invocation.getArgument(0)).setUserId(12L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        service.register(dto);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(saved.capture());
        assertEquals("student01", saved.getValue().getUsername());
        assertEquals("同学甲", saved.getValue().getNickname());
    }

    @Test
    void registrationTurnsConcurrentAccountCollisionIntoBusinessError() {
        doThrow(new DuplicateKeyException("duplicate username"))
                .when(userMapper).insert(any(User.class));

        assertThrows(BusinessException.class, () -> service.register(registration("student01")));
    }

    @Test
    void productionSmsSendDoesNotPretendAnUnconfiguredProviderDeliveredACode() {
        assertThrows(BusinessException.class, () -> service.sendSmsCode("13900000000"));
        verify(smsCodeMapper, never()).insert(any(SmsCode.class));
    }

    @Test
    void productionSmsLoginIsUnavailableWithoutAProvider() {
        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
        verify(smsCodeMapper, never()).selectLatestByPhone(anyString(), eq(1));
    }

    @Test
    void mockSmsRequiresAnActuallySentDatabaseCode() {
        enableMockSms();
        lenient().when(userMapper.selectByPhone("13900000000")).thenReturn(activeUser());

        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
    }

    @Test
    void mockSmsRejectsExpiredCode() {
        enableMockSms();
        SmsCode code = sentCode();
        code.setExpireTime(LocalDateTime.now().minusMinutes(1));
        lenient().when(smsCodeMapper.selectLatestByPhone("13900000000", 1)).thenReturn(code);
        lenient().when(userMapper.selectByPhone("13900000000")).thenReturn(activeUser());

        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
    }

    @Test
    void mockSmsRejectsAlreadyConsumedCode() {
        enableMockSms();
        SmsCode code = sentCode();
        code.setIsUsed(1);
        lenient().when(smsCodeMapper.selectLatestByPhone("13900000000", 1)).thenReturn(code);
        lenient().when(userMapper.selectByPhone("13900000000")).thenReturn(activeUser());

        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
    }

    @Test
    void mockSmsLoginConsumesCodeAndSignsAnExistingActiveAccount() {
        enableMockSms();
        lenient().when(smsCodeMapper.selectLatestByPhone("13900000000", 1)).thenReturn(sentCode());
        lenient().when(smsCodeMapper.markUsed(23L)).thenReturn(1);
        when(userMapper.selectByPhone("13900000000")).thenReturn(activeUser());

        var result = service.smsLogin("13900000000", "123456");

        assertEquals(200, result.getCode());
        verify(smsCodeMapper).markUsed(23L);
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void smsLoginRejectsCodeLostToAConcurrentConsumer() {
        enableMockSms();
        lenient().when(smsCodeMapper.selectLatestByPhone("13900000000", 1)).thenReturn(sentCode());
        lenient().when(userMapper.selectByPhone("13900000000")).thenReturn(activeUser());

        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
    }

    @Test
    void smsLoginRejectsDisabledAccountBeforeIssuingTokens() {
        enableMockSms();
        User user = activeUser();
        user.setStatus(0);
        lenient().when(smsCodeMapper.selectLatestByPhone("13900000000", 1)).thenReturn(sentCode());
        lenient().when(smsCodeMapper.markUsed(23L)).thenReturn(1);
        when(userMapper.selectByPhone("13900000000")).thenReturn(user);

        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
    }

    @Test
    void smsLoginNeverCreatesAnAccountFromAPhoneNumber() {
        enableMockSms();
        lenient().when(smsCodeMapper.selectLatestByPhone("13900000000", 1)).thenReturn(sentCode());
        lenient().when(smsCodeMapper.markUsed(23L)).thenReturn(1);
        lenient().doAnswer(invocation -> {
            ((User) invocation.getArgument(0)).setUserId(12L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        assertThrows(BusinessException.class, () -> service.smsLogin("13900000000", "123456"));
        verify(userMapper, never()).insert(any(User.class));
    }

    private void enableMockSms() {
        ReflectionTestUtils.setField(service, "smsMock", true);
        ReflectionTestUtils.setField(service, "smsMockCode", "123456");
    }

    private static User activeUser() {
        User user = new User();
        user.setUserId(12L);
        user.setUsername("student01");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("123456"));
        user.setPhone("13900000000");
        user.setNickname("同学甲");
        user.setStatus(1);
        user.setUserType(1);
        return user;
    }

    private static SmsCode sentCode() {
        SmsCode code = new SmsCode();
        code.setSmsId(23L);
        code.setPhone("13900000000");
        code.setCode("123456");
        code.setType(1);
        code.setIsUsed(0);
        code.setExpireTime(LocalDateTime.now().plusMinutes(5));
        return code;
    }

    private static RegisterDTO registration(String account) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(account);
        dto.setPassword("123456");
        dto.setNickname("同学甲");
        return dto;
    }
}
