package com.bugaoshan.security;

import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void bothTokenTypesCarryTheCredentialVersion() {
        var key = Keys.hmacShaKeyFor("test-secret-key-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8));
        for (String token : new String[]{jwtUtil.generateAccessToken(7L, 3), jwtUtil.generateRefreshToken(7L)}) {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            assertEquals(0, claims.get("credential_version", Integer.class));
        }
    }
}
