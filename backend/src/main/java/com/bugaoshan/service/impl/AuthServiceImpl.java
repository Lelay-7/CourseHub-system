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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = generateTokens(user.getUserId(), user.getUserType());
        return Result.ok(data);
    }

    @Override
    public Result smsLogin(String phone, String code) {
        if (smsMock) {
            if (smsMockCode == null || smsMockCode.isBlank() || !smsMockCode.equals(code)) {
                throw new BusinessException("验证码错误或已过期");
            }
        } else {
            SmsCode smsCode = smsCodeMapper.selectLatestByPhone(phone, 1);
            if (smsCode == null || smsCode.getIsUsed() == 1
                    || smsCode.getExpireTime().isBefore(LocalDateTime.now())
                    || !smsCode.getCode().equals(code)) {
                throw new BusinessException("验证码错误或已过期");
            }
            smsCodeMapper.markUsed(smsCode.getSmsId());
        }

        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            user = new User();
            user.setUsername(phone);
            user.setPasswordHash(passwordEncoder.encode(phone));
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setPhone(phone);
            user.setUserType(1);
            user.setStatus(1);
            user.setScoreBalance(0);
            user.setTotalScore(0);
            user.setIsDeleted(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            userMapper.insert(user);
        }
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = generateTokens(user.getUserId(), user.getUserType());
        return Result.ok(data);
    }

    @Override
    public Result sendSmsCode(String phone) {
        if (smsMock && (smsMockCode == null || smsMockCode.isBlank())) {
            throw new BusinessException("模拟短信验证码未配置");
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                "sms:send:" + phone, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        String code = smsMock ? smsMockCode : String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        SmsCode smsCode = new SmsCode();
        smsCode.setPhone(phone);
        smsCode.setCode(code);
        smsCode.setType(1);
        smsCode.setIsUsed(0);
        smsCode.setExpireTime(LocalDateTime.now().plusMinutes(5));
        smsCode.setCreateTime(LocalDateTime.now());
        smsCodeMapper.insert(smsCode);

        log.debug("SMS code created for phone ending in {}", phone.substring(Math.max(0, phone.length() - 4)));

        Map<String, Object> data = new HashMap<>();
        data.put("sent", true);
        return Result.ok(data);
    }

    @Override
    public Result register(RegisterDTO dto) {
        User existingUser = userMapper.selectByUsername(dto.getUsername());
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            User existingPhone = userMapper.selectByPhone(dto.getPhone());
            if (existingPhone != null) {
                throw new BusinessException("手机号已被注册");
            }
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
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
        userMapper.insert(user);

        Map<String, Object> data = generateTokens(user.getUserId(), user.getUserType());
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
        Map<String, Object> data = generateTokens(userId, user.getUserType());
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

    private Map<String, Object> generateTokens(Long userId, Integer userType) {
        String accessToken = jwtUtil.generateAccessToken(userId, userType);
        String refreshToken = jwtUtil.generateRefreshToken(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        return result;
    }
}
