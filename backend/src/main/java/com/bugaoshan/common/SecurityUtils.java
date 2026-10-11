package com.bugaoshan.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        throw new BusinessException(401, "未登录");
    }

    public static Integer getCurrentUserType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof com.bugaoshan.security.AuthenticatedSession session) return session.userType();
        if (auth != null && auth.getDetails() instanceof Integer) {
            return (Integer) auth.getDetails();
        }
        return null;
    }
    public static int getCurrentCredentialVersion() {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getDetails() instanceof com.bugaoshan.security.AuthenticatedSession session) return session.credentialVersion();
        throw new ChatException(401,"登录已失效，请重新登录");
    }
}
