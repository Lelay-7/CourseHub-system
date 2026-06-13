package com.bugaoshan.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-key-that-is-long-enough-for-hs256",
            60_000,
            120_000
    );

    @Test
    void distinguishesAccessAndRefreshTokens() {
        String accessToken = jwtUtil.generateAccessToken(7L, 3);
        String refreshToken = jwtUtil.generateRefreshToken(7L);

        assertTrue(jwtUtil.isAccessToken(accessToken));
        assertFalse(jwtUtil.isRefreshToken(accessToken));
        assertTrue(jwtUtil.isRefreshToken(refreshToken));
        assertFalse(jwtUtil.isAccessToken(refreshToken));
    }
}
