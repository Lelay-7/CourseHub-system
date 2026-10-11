package com.bugaoshan.security;

import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRefreshTokenForApiAuthentication() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userMapper);
        MockHttpServletRequest request = requestWithToken("refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(jwtUtil.isAccessToken("refresh-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsTokenWhenPersistedUserIsDisabled() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userMapper);
        MockHttpServletRequest request = requestWithToken("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = new User();
        user.setUserId(12L);
        user.setUserType(3);
        user.setStatus(0);
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isAccessToken("access-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("access-token")).thenReturn(12L);
        when(userMapper.selectById(12L)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/course/list");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void realAccessTokensMustMatchThePersistedVersionAndLegacyTokensAreRejected() throws Exception {
        var jwt = new JwtUtil("test-secret-key-that-is-long-enough-for-hs256", 60000, 120000);
        User user = new User(); user.setUserId(12L); user.setStatus(1); user.setUserType(1); user.setCredentialVersion(2);
        when(userMapper.selectById(12L)).thenReturn(user);
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "test-secret-key-that-is-long-enough-for-hs256".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String legacy = io.jsonwebtoken.Jwts.builder().subject("12").claim("type", "access")
                .expiration(new java.util.Date(System.currentTimeMillis()+60000)).signWith(key).compact();
        var filter = new JwtAuthenticationFilter(jwt, userMapper);
        for(String token : new String[]{legacy,jwt.generateAccessToken(12L,1,1)}) {
            filter.doFilter(requestWithToken(token),new MockHttpServletResponse(),filterChain);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        filter.doFilter(requestWithToken(jwt.generateAccessToken(12L,1,2)),new MockHttpServletResponse(),filterChain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
