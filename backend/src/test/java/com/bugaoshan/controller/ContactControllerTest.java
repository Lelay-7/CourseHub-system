package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Friend;
import com.bugaoshan.entity.User;
import com.bugaoshan.entity.ContactAuth;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
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

    @Test
    void hiddenContactFieldsDoNotLeakFromTeacherCard() {
        login(99L);
        User user = contact(10L, 2);
        when(userMapper.selectById(10L)).thenReturn(user);
        when(contactAuthMapper.selectByUserId(10L)).thenReturn(privacy(10L, 1, 1, 3));

        Map<?, ?> card = (Map<?, ?>) controller().detail(10L).getData();

        assertEquals("reader10", card.get("username"));
        assertEquals("朋友10", card.get("nickname"));
        assertFalse(card.containsKey("real_name"));
        assertFalse(card.containsKey("student_no"));
        assertFalse(card.containsKey("phone"));
        assertFalse(card.containsKey("email"));
    }

    @Test
    void publicContactScopeAllowsNonFriendToReadEnabledFields() {
        login(99L);
        when(userMapper.selectById(10L)).thenReturn(contact(10L, 1));
        when(contactAuthMapper.selectByUserId(10L)).thenReturn(privacy(10L, 1, 2, 1));

        Map<?, ?> card = (Map<?, ?>) controller().detail(10L).getData();

        assertEquals("13800000010", card.get("phone"));
        assertEquals("姓名10", card.get("real_name"));
        assertFalse(card.containsKey("email"));
    }

    @Test
    void acceptedFriendStillCannotReadSelfOnlyFields() {
        login(99L);
        when(userMapper.selectById(10L)).thenReturn(contact(10L, 1));
        when(contactAuthMapper.selectByUserId(10L)).thenReturn(privacy(10L, 1, 1, 3));
        Friend friendship = new Friend();
        friendship.setStatus(1);
        when(friendMapper.selectByUserAndTarget(99L, 10L)).thenReturn(friendship);

        Map<?, ?> card = (Map<?, ?>) controller().detail(10L).getData();

        assertEquals("accepted", card.get("friend_status"));
        assertFalse(card.containsKey("phone"));
        assertFalse(card.containsKey("real_name"));
    }

    @Test
    void ownerCanReadOwnFieldsEvenWhenHidden() {
        login(10L);
        when(userMapper.selectById(10L)).thenReturn(contact(10L, 1));

        Map<?, ?> card = (Map<?, ?>) controller().detail(10L).getData();

        assertEquals("self", card.get("friend_status"));
        assertEquals("13800000010", card.get("phone"));
        assertEquals("owner10@example.test", card.get("email"));
    }

    @Test
    void searchDoesNotRevealNameThroughMatchingWhenItIsHidden() {
        login(99L);
        when(userMapper.selectByUserType(anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == 2 ? List.of(contact(10L, 2)) : List.of());
        when(contactAuthMapper.selectByUserId(10L)).thenReturn(privacy(10L, 1, 1, 3));

        List<?> cards = (List<?>) controller().search("姓名10").getData();

        assertTrue(cards.isEmpty());
    }

    @Test
    void pendingRequestsDoNotBypassContactPrivacy() {
        login(99L);
        when(friendMapper.selectPendingRequests(99L)).thenReturn(List.of(Map.of(
                "friend_id", 5L, "user_id", 10L, "real_name", "姓名10", "direction", "received")));
        when(userMapper.selectById(10L)).thenReturn(contact(10L, 1));
        when(contactAuthMapper.selectByUserId(10L)).thenReturn(privacy(10L, 1, 1, 3));

        List<?> rows = (List<?>) controller().pending().getData();
        Map<?, ?> row = (Map<?, ?>) rows.get(0);

        assertEquals(5L, row.get("friend_id"));
        assertEquals("received", row.get("direction"));
        assertFalse(row.containsKey("real_name"));
    }

    @Test
    void partialPrivacyUpdatePreservesOtherValuesAndServerOwnedIdentity() {
        login(99L);
        ContactAuth existing = privacy(99L, 1, 1, 2);
        existing.setAuthId(8L);
        when(contactAuthMapper.selectByUserId(99L)).thenReturn(existing);
        ContactAuth submitted = new ContactAuth();
        submitted.setAuthId(666L);
        submitted.setUserId(10L);
        submitted.setPhoneVisible(2);

        controller().updateAuth(submitted);

        ArgumentCaptor<ContactAuth> saved = ArgumentCaptor.forClass(ContactAuth.class);
        verify(contactAuthMapper).updateAuth(saved.capture());
        assertEquals(8L, saved.getValue().getAuthId());
        assertEquals(99L, saved.getValue().getUserId());
        assertEquals(2, saved.getValue().getPhoneVisible());
        assertEquals(1, saved.getValue().getEmailVisible());
        assertEquals(2, saved.getValue().getContactVisible());
    }

    @Test
    void newPrivacySettingsIgnoreClientPrimaryKeyAndValidateEnums() {
        login(99L);
        ContactAuth submitted = privacy(10L, 1, 2, 1);
        submitted.setAuthId(666L);

        controller().updateAuth(submitted);

        ArgumentCaptor<ContactAuth> saved = ArgumentCaptor.forClass(ContactAuth.class);
        verify(contactAuthMapper).insert(saved.capture());
        assertEquals(99L, saved.getValue().getUserId());
        assertEquals(null, saved.getValue().getAuthId());
    }

    @Test
    void invalidPrivacyEnumIsRejectedWithoutWriting() {
        login(99L);
        ContactAuth submitted = privacy(99L, 0, 2, 1);

        assertThrows(BusinessException.class, () -> controller().updateAuth(submitted));

        verify(contactAuthMapper, never()).updateAuth(any());
        verify(contactAuthMapper, never()).insert(any(ContactAuth.class));
    }

    private ContactController controller() {
        return new ContactController(contactService, contactAuthMapper, friendMapper, userMapper);
    }

    private void login(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null));
    }

    private User contact(Long id, int userType) {
        User user = new User();
        user.setUserId(id);
        user.setUserType(userType);
        user.setUsername("reader" + id);
        user.setNickname("朋友" + id);
        user.setRealName("姓名" + id);
        user.setStudentNo("20240000" + id);
        user.setPhone("138000000" + id);
        user.setEmail("owner" + id + "@example.test");
        return user;
    }

    private ContactAuth privacy(Long id, int phone, int email, int scope) {
        ContactAuth auth = new ContactAuth();
        auth.setUserId(id);
        auth.setPhoneVisible(phone);
        auth.setEmailVisible(email);
        auth.setContactVisible(scope);
        return auth;
    }
}
