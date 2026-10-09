package com.bugaoshan.common;

import com.bugaoshan.entity.ContactAuth;
import com.bugaoshan.entity.User;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One privacy rule for search, cards, friends and the legacy contact list. */
public final class ContactCardPolicy {
    private ContactCardPolicy() {}

    public static Map<String, Object> build(User user, Long viewerId, ContactAuth auth, String friendStatus) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("user_id", user.getUserId());
        card.put("username", user.getUsername());
        card.put("nickname", user.getNickname());
        card.put("avatarUrl", user.getAvatarUrl());
        card.put("user_type", user.getUserType());
        card.put("department", user.getDepartment());
        card.put("friend_status", friendStatus);

        boolean owner = user.getUserId().equals(viewerId);
        int scope = auth == null || auth.getContactVisible() == null ? 2 : auth.getContactVisible();
        boolean visible = owner || scope == 1 || (scope == 2 && "accepted".equals(friendStatus));
        if (visible) {
            card.put("real_name", user.getRealName());
            card.put("student_no", user.getStudentNo());
            card.put("class_name", user.getClassName());
            card.put("title", user.getTitle());
            card.put("office_location", user.getOfficeLocation());
            // Each field switch remains effective within the chosen contact scope.
            if (owner || (auth != null && Integer.valueOf(1).equals(auth.getPhoneVisible()))) {
                card.put("phone", user.getPhone());
                card.put("office_phone", user.getOfficePhone());
            }
            if (owner || (auth != null && Integer.valueOf(1).equals(auth.getEmailVisible()))) {
                card.put("email", user.getEmail());
            }
        }
        return card;
    }

    public static boolean matches(Map<String, Object> card, String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        // Search only fields the caller may read, so hidden names cannot be inferred.
        return java.util.stream.Stream.of("nickname", "real_name", "username", "student_no", "department")
                .anyMatch(key -> card.get(key) != null
                        && card.get(key).toString().toLowerCase(Locale.ROOT).contains(query));
    }
}
