package com.fvps.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        ReflectionTestUtils.setField(jwtService, "secretKey", secretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1000 * 60 * 60);

        userDetails = new User("test@fvps.com", "pass", Collections.emptyList());
    }

    @Test
    void shouldGenerateValidToken_andExtractUsername() {
        String token = jwtService.generateToken(userDetails);

        assertNotNull(token);
        String username = jwtService.extractUsername(token);
        assertEquals("test@fvps.com", username);
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void shouldFailValidation_whenUsernameDoesNotMatch() {
        String token = jwtService.generateToken(userDetails);

        UserDetails otherUser = new User("other@fvps.com", "pass", Collections.emptyList());

        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void shouldThrowExpiredException_whenTokenIsExpired() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);

        String token = jwtService.generateToken(userDetails);

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
    }

    @Test
    void shouldThrowSignatureException_whenTokenIsTampered() {
        String token = jwtService.generateToken(userDetails);

        String tamperedToken = token.substring(0, token.length() - 1) + "X";

        assertThrows(SignatureException.class, () -> jwtService.extractUsername(tamperedToken));
    }

    @Test
    void shouldThrowMalformedException_whenTokenIsGarbage() {
        String garbageToken = "this.is.not.a.token";

        assertThrows(MalformedJwtException.class, () -> jwtService.extractUsername(garbageToken));
    }
}