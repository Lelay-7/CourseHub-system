package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.dto.RegisterDTO;
import com.bugaoshan.entity.SmsCode;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.SmsCodeMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.security.JwtUtil;
import com.bugaoshan.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserMapper userMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${sms.mock:false}")
    private boolean smsMock;

    @Value("${sms.mock-code:}")
    private String smsMockCode;

    public AuthServiceImpl(UserMapper userMapper, SmsCodeMapper smsCodeMapper,
                           JwtUtil jwtUtil, BCryptPasswordEncoder passwordEncoder,
                           StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.smsCodeMapper = smsCodeMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Result login(String username, String password) {
        String account = normalizeAccount(username);
        validatePassword(password);
        User user = userMapper.selectByUsername(account);
        if (user == null) {
            throw new BusinessException("账号或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        requireActiveUser(user);
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateLastLogin(user.getUserId());

        Map<String, Object> data = generateTokens(user);
        return Result.ok(data);
    }

    @Override
    public Result smsLogin(String phone, String code) {
        requireSmsAvailable();
        String normalizedPhone = normalizePhone(phone);
        SmsCode smsCode = smsCodeMapper.selectLatestByPhone(normalizedPhone, 1);
        if (smsCode == null || !Objects.equals(smsCode.getIsUsed(), 0)
                || smsCode.getExpireTime() == null
                || !smsCode.getExpireTime().isAfter(LocalDateTime.now())
                || code == null || !code.equals(smsCode.getCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        User user = userMapper.selectByPhone(normalizedPhone);
        if (user == null) {
            throw new BusinessException("该手机号未关联账号，请使用独立账号注册");
        }
        requireActiveUser(user);
        // The conditional update lets only one concurrent request consume this code.
        if (smsCodeMapper.markUsed(smsCode.getSmsId()) != 1) {
            throw new BusinessException("验证码错误或已过期");
        }
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateLastLogin(user.getUserId());

        Map<String, Object> data = generateTokens(user);
        return Result.ok(data);
    }

    @Override
    public Result sendSmsCode(String phone) {
        requireSmsAvailable();
        String normalizedPhone = normalizePhone(phone);
        if (smsMockCode == null || !smsMockCode.matches("\\d{6}")) {
            throw new BusinessException("模拟短信验证码未配置");
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                "sms:send:" + normalizedPhone, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        SmsCode smsCode = new SmsCode();
        smsCode.setPhone(normalizedPhone);
        smsCode.setCode(smsMockCode);
        smsCode.setType(1);
        smsCode.setIsUsed(0);
        smsCode.setExpireTime(LocalDateTime.now().plusMinutes(5));
        smsCode.setCreateTime(LocalDateTime.now());
        smsCodeMapper.insert(smsCode);

        log.debug("SMS code created for phone ending in {}", normalizedPhone.substring(normalizedPhone.length() - 4));

        Map<String, Object> data = new HashMap<>();
        data.put("sent", true);
        return Result.ok(data);
    }

    @Override
    public Result register(RegisterDTO dto) {
        if (dto == null) throw new BusinessException("注册信息不能为空");
        String account = normalizeAccount(dto.getUsername());
        validatePassword(dto.getPassword());
        String nickname = dto.getNickname() == null ? account : normalizeOptional(dto.getNickname());
        if (nickname == null || nickname.length() < 2 || nickname.length() > 20) {
            throw new BusinessException("昵称长度需在2-20之间");
        }
        String phone = normalizeOptional(dto.getPhone());
        if (phone != null) phone = normalizePhone(phone);
        User existingUser = userMapper.selectByUsername(account);
        if (existingUser != null) {
            throw new BusinessException(409, "登录账号已存在");
        }
        if (phone != null) {
            User existingPhone = userMapper.selectByPhone(phone);
            if (existingPhone != null) {
                throw new BusinessException(409, "手机号已被注册");
            }
        }

        User user = new User();
        user.setUsername(account);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setRealName(dto.getRealName());
        user.setDepartment(dto.getDepartment());
        user.setClassName(dto.getClassName());
        user.setStudentNo(dto.getStudentNo());
        user.setUserType(1);
        user.setStatus(1);
        user.setScoreBalance(0);
        user.setTotalScore(0);
        user.setIsDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "登录账号或手机号已被使用");
        }

        Map<String, Object> data = generateTokens(user);
        return Result.ok(data);
    }

    @Override
    public Result refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException("refreshToken无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户不存在或已被禁用");
        }
        if (!jwtUtil.matchesCredentialVersion(refreshToken, user.getCredentialVersion())) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        Map<String, Object> data = generateTokens(user);
        return Result.ok(data);
    }

    @Override
    public Result getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setPasswordHash(null);
        }
        return Result.ok(user);
    }

    private Map<String, Object> generateTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUserType(), user.getCredentialVersion());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getCredentialVersion());
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        return result;
    }

    private String normalizeAccount(String account) {
        String normalized = normalizeOptional(account);
        if (normalized == null || normalized.length() < 3 || normalized.length() > 20) {
            throw new BusinessException("登录账号长度需在3-20之间");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 20) {
            throw new BusinessException("密码长度需在6-20之间");
        }
    }

    private String normalizePhone(String phone) {
        String normalized = normalizeOptional(phone);
        if (normalized == null || !normalized.matches("1[3-9]\\d{9}")) {
            throw new BusinessException("手机号格式不正确");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private void requireActiveUser(User user) {
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException("账号已被禁用");
        }
    }

    private void requireSmsAvailable() {
        if (!smsMock) {
            throw new BusinessException("短信服务尚未配置，请使用账号密码登录");
        }
    }
}
