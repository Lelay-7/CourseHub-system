package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Friend;
import com.bugaoshan.mapper.ContactAuthMapper;
import com.bugaoshan.mapper.FriendMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.ContactService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.annotation.TableId;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    @Mock private ContactService contactService;
    @Mock private ContactAuthMapper contactAuthMapper;
    @Mock private FriendMapper friendMapper;
    @Mock private UserMapper userMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonParticipantCannotRemoveFriendship() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(99L, null));
        Friend friend = new Friend();
        friend.setFriendId(5L);
        friend.setUserId(10L);
        friend.setTargetId(11L);
        when(friendMapper.selectById(5L)).thenReturn(friend);
        ContactController controller = new ContactController(
                contactService, contactAuthMapper, friendMapper, userMapper);

        assertThrows(BusinessException.class, () -> controller.remove(5L));
        verify(friendMapper, never()).deleteById(5L);
    }

    @Test
    void friendPrimaryKeyIsMappedForBaseMapperOperations() throws Exception {
        assertNotNull(Friend.class.getDeclaredField("friendId").getAnnotation(TableId.class));
    }
}
