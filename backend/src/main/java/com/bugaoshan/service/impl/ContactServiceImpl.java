package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.ContactCardPolicy;
import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.entity.Friend;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.ContactAuthMapper;
import com.bugaoshan.mapper.FriendMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.ContactService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ContactServiceImpl implements ContactService {
    private final UserMapper userMapper;
    private final ContactAuthMapper contactAuthMapper;
    private final FriendMapper friendMapper;

    public ContactServiceImpl(UserMapper userMapper, ContactAuthMapper contactAuthMapper, FriendMapper friendMapper) {
        this.userMapper = userMapper;
        this.contactAuthMapper = contactAuthMapper;
        this.friendMapper = friendMapper;
    }

    @Override
    public Result getContactList(String type, String keyword) {
        Long viewerId = SecurityUtils.getCurrentUserId();
        List<Integer> roles = "teacher".equals(type) ? List.of(2)
                : "student".equals(type) ? List.of(1) : List.of(1, 2, 3);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int role : roles) {
            for (User user : userMapper.selectByUserType(role)) {
                if (viewerId.equals(user.getUserId())) continue;
                Map<String, Object> card = buildCard(user, viewerId);
                if (ContactCardPolicy.matches(card, keyword)) list.add(card);
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
        if (user == null) throw new BusinessException("用户不存在");
        return Result.ok(buildCard(user, SecurityUtils.getCurrentUserId()));
    }

    private Map<String, Object> buildCard(User user, Long viewerId) {
        if (viewerId.equals(user.getUserId())) return ContactCardPolicy.build(user, viewerId, null, "self");
        Friend outgoing = friendMapper.selectByUserAndTarget(viewerId, user.getUserId());
        Friend incoming = friendMapper.selectByUserAndTarget(user.getUserId(), viewerId);
        String status = "none";
        if (isStatus(outgoing, 1) || isStatus(incoming, 1)) status = "accepted";
        else if (isStatus(outgoing, 0)) status = "pending";
        else if (isStatus(incoming, 0)) status = "pending_receive";
        return ContactCardPolicy.build(user, viewerId, contactAuthMapper.selectByUserId(user.getUserId()), status);
    }

    private boolean isStatus(Friend friend, int status) {
        return friend != null && Integer.valueOf(status).equals(friend.getStatus());
    }
}
