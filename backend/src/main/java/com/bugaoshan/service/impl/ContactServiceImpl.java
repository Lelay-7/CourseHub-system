package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.entity.ContactAuth;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.ContactAuthMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.ContactService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContactServiceImpl implements ContactService {

    private final UserMapper userMapper;
    private final ContactAuthMapper contactAuthMapper;

    public ContactServiceImpl(UserMapper userMapper, ContactAuthMapper contactAuthMapper) {
        this.userMapper = userMapper;
        this.contactAuthMapper = contactAuthMapper;
    }

    @Override
    public Result getContactList(String type, String keyword) {
        Integer userType = "teacher".equals(type) ? 2 : 1;
        List<User> users = userMapper.selectByUserType(userType);

        List<Map<String, Object>> list = new ArrayList<>();
        for (User user : users) {
            if (user.getPasswordHash() != null) {
                user.setPasswordHash(null);
            }
            if (keyword != null && !keyword.isEmpty()) {
                boolean matches = false;
                if (user.getNickname() != null && user.getNickname().contains(keyword)) matches = true;
                if (user.getRealName() != null && user.getRealName().contains(keyword)) matches = true;
                if (user.getDepartment() != null && user.getDepartment().contains(keyword)) matches = true;
                if (!matches) continue;
            }

            if (userType == 1) {
                Map<String, Object> map = buildStudentMap(user);
                list.add(map);
            } else {
                list.add(toMap(user));
            }
        }

        return Result.ok(list);
    }

    @Override
    public Result searchContact(String type, String keyword) {
        return getContactList(type, keyword);
    }

    @Override
    public Result getContactDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getUserId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("department", user.getDepartment());
        result.put("className", user.getClassName());
        result.put("userType", user.getUserType());
        result.put("totalScore", user.getTotalScore());

        if (user.getUserType() == 2) {
            result.put("realName", user.getRealName());
            result.put("title", user.getTitle());
            result.put("officeLocation", user.getOfficeLocation());
            result.put("officePhone", user.getOfficePhone());
        } else {
            ContactAuth auth = contactAuthMapper.selectByUserId(userId);
            if (auth != null && auth.getContactVisible() != null && auth.getContactVisible() == 1) {
                result.put("realName", user.getRealName());
                result.put("studentNo", user.getStudentNo());
                if (auth.getPhoneVisible() != null && auth.getPhoneVisible() == 1) {
                    result.put("phone", desensitizePhone(user.getPhone()));
                }
                if (auth.getEmailVisible() != null && auth.getEmailVisible() == 1) {
                    result.put("email", user.getEmail());
                }
            }
        }

        return Result.ok(result);
    }

    private Map<String, Object> buildStudentMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getUserId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatarUrl", user.getAvatarUrl());
        map.put("department", user.getDepartment());
        map.put("className", user.getClassName());
        map.put("userType", user.getUserType());
        map.put("totalScore", user.getTotalScore());

        ContactAuth auth = contactAuthMapper.selectByUserId(user.getUserId());
        if (auth != null && auth.getContactVisible() != null && auth.getContactVisible() == 1) {
            map.put("realName", user.getRealName());
            map.put("studentNo", user.getStudentNo());
            if (auth.getPhoneVisible() != null && auth.getPhoneVisible() == 1) {
                map.put("phone", desensitizePhone(user.getPhone()));
            }
            if (auth.getEmailVisible() != null && auth.getEmailVisible() == 1) {
                map.put("email", user.getEmail());
            }
        }
        return map;
    }

    private Map<String, Object> toMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getUserId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatarUrl", user.getAvatarUrl());
        map.put("realName", user.getRealName());
        map.put("userType", user.getUserType());
        map.put("department", user.getDepartment());
        map.put("title", user.getTitle());
        map.put("officeLocation", user.getOfficeLocation());
        map.put("officePhone", user.getOfficePhone());
        map.put("phone", user.getPhone());
        map.put("email", user.getEmail());
        map.put("totalScore", user.getTotalScore());
        return map;
    }

    private String desensitizePhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
