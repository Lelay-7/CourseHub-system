package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.entity.ContactAuth;
import com.bugaoshan.entity.Friend;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.ContactAuthMapper;
import com.bugaoshan.mapper.FriendMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.ContactService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;
    private final ContactAuthMapper contactAuthMapper;
    private final FriendMapper friendMapper;
    private final UserMapper userMapper;

    public ContactController(ContactService contactService, ContactAuthMapper contactAuthMapper,
                             FriendMapper friendMapper, UserMapper userMapper) {
        this.contactService = contactService;
        this.contactAuthMapper = contactAuthMapper;
        this.friendMapper = friendMapper;
        this.userMapper = userMapper;
    }

    /** 搜索用户 */
    @GetMapping("/search")
    public Result<?> search(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Long myId = SecurityUtils.getCurrentUserId();
        List<User> teachers = userMapper.selectByUserType(2);
        List<Map<String, Object>> results = new ArrayList<>();
        for (User u : teachers) {
            if (matches(u, keyword)) {
                results.add(buildCard(u, myId));
            }
        }
        List<User> students = userMapper.selectByUserType(1);
        for (User u : students) {
            if (matches(u, keyword)) {
                results.add(buildCard(u, myId));
            }
        }
        return Result.ok(results);
    }

    /** 获取联系人列表（按类型筛选） */
    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) String type,
                           @RequestParam(required = false) String keyword) {
        return contactService.getContactList(type, keyword);
    }

    private boolean matches(User u, String keyword) {
        String k = keyword.toLowerCase();
        return (u.getNickname() != null && u.getNickname().toLowerCase().contains(k))
            || (u.getRealName() != null && u.getRealName().toLowerCase().contains(k))
            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(k))
            || (u.getDepartment() != null && u.getDepartment().toLowerCase().contains(k));
    }

    private Map<String, Object> buildCard(User u, Long myId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", u.getUserId());
        m.put("nickname", u.getNickname());
        m.put("real_name", u.getRealName());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("user_type", u.getUserType());
        m.put("department", u.getDepartment());
        m.put("title", u.getTitle());
        m.put("class_name", u.getClassName());
        // Check friend status
        Friend f = friendMapper.selectByUserAndTarget(myId, u.getUserId());
        Friend rev = friendMapper.selectByUserAndTarget(u.getUserId(), myId);
        if ((f != null && Integer.valueOf(1).equals(f.getStatus())) || (rev != null && Integer.valueOf(1).equals(rev.getStatus()))) {
            m.put("friend_status", "accepted");
            ContactAuth auth = contactAuthMapper.selectByUserId(u.getUserId());
            if (auth != null && Integer.valueOf(1).equals(auth.getPhoneVisible())) {
                m.put("phone", u.getPhone());
            }
            if (auth != null && Integer.valueOf(1).equals(auth.getEmailVisible())) {
                m.put("email", u.getEmail());
            }
        } else if (f != null && Integer.valueOf(0).equals(f.getStatus())) {
            m.put("friend_status", "pending");
        } else if (rev != null && Integer.valueOf(0).equals(rev.getStatus())) {
            m.put("friend_status", "pending_receive");
        } else {
            m.put("friend_status", "none");
        }
        return m;
    }

    /** 好友列表 */
    @GetMapping("/my-friends")
    public Result<?> myFriends() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(friendMapper.selectMyFriends(userId));
    }

    /** 待处理的好友申请 */
    @GetMapping("/pending")
    public Result<?> pending() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(friendMapper.selectPendingRequests(userId));
    }

    /** 发送好友申请 */
    @PostMapping("/add/{targetId}")
    public Result<?> addFriend(@PathVariable Long targetId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId.equals(targetId)) throw new BusinessException("不能添加自己");
        Friend existing = friendMapper.selectByUserAndTarget(userId, targetId);
        Friend rev = friendMapper.selectByUserAndTarget(targetId, userId);
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getStatus())) throw new BusinessException("已经是好友");
            if (Integer.valueOf(0).equals(existing.getStatus())) throw new BusinessException("已发送申请，等待对方同意");
            existing.setStatus(0);
            existing.setUpdateTime(LocalDateTime.now());
            friendMapper.updateById(existing);
            return Result.ok();
        }
        if (rev != null && Integer.valueOf(0).equals(rev.getStatus())) {
            rev.setStatus(1);
            rev.setUpdateTime(LocalDateTime.now());
            friendMapper.updateById(rev);
            return Result.ok(Map.of("message", "已接受对方的好友申请"));
        }
        Friend f = new Friend();
        f.setUserId(userId);
        f.setTargetId(targetId);
        f.setStatus(0);
        f.setCreateTime(LocalDateTime.now());
        f.setUpdateTime(LocalDateTime.now());
        friendMapper.insert(f);
        return Result.ok();
    }

    /** 同意申请 */
    @PostMapping("/accept/{friendId}")
    public Result<?> accept(@PathVariable Long friendId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (friendMapper.updateStatus(friendId, userId, 1) == 0) {
            throw new BusinessException("好友申请不存在或已处理");
        }
        return Result.ok();
    }

    /** 拒绝或删除好友 */
    @DeleteMapping("/remove/{friendId}")
    public Result<?> remove(@PathVariable Long friendId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Friend friend = friendMapper.selectById(friendId);
        if (friend == null || (!userId.equals(friend.getUserId()) && !userId.equals(friend.getTargetId()))) {
            throw new BusinessException("好友关系不存在或无权操作");
        }
        friendMapper.deleteById(friendId);
        return Result.ok();
    }

    /** 联系人详情（名片） */
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        Long myId = SecurityUtils.getCurrentUserId();
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        return Result.ok(buildCard(u, myId));
    }

    /** 隐私设置 */
    @GetMapping("/auth/my")
    public Result<?> myAuth() {
        Long userId = SecurityUtils.getCurrentUserId();
        ContactAuth auth = contactAuthMapper.selectByUserId(userId);
        if (auth == null) {
            auth = new ContactAuth();
            auth.setUserId(userId);
            auth.setPhoneVisible(2);
            auth.setEmailVisible(2);
            auth.setContactVisible(2);
            auth.setCreateTime(LocalDateTime.now());
            auth.setUpdateTime(LocalDateTime.now());
            contactAuthMapper.insert(auth);
        }
        return Result.ok(auth);
    }

    @PutMapping("/auth/update")
    public Result<?> updateAuth(@RequestBody ContactAuth auth) {
        Long userId = SecurityUtils.getCurrentUserId();
        ContactAuth existing = contactAuthMapper.selectByUserId(userId);
        if (existing != null) {
            existing.setPhoneVisible(auth.getPhoneVisible());
            existing.setEmailVisible(auth.getEmailVisible());
            existing.setContactVisible(auth.getContactVisible());
            existing.setUpdateTime(LocalDateTime.now());
            contactAuthMapper.updateAuth(existing);
        } else {
            auth.setUserId(userId);
            auth.setCreateTime(LocalDateTime.now());
            auth.setUpdateTime(LocalDateTime.now());
            contactAuthMapper.insert(auth);
        }
        return Result.ok();
    }
}
