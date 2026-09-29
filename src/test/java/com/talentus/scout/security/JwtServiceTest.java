package com.talentus.scout.security;

import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey("super_secret_jwt_key_local_64_chars_talentus_scout_2026_dev_for_security");
        properties.setExpirationTime(3600000); // 1 hora
        jwtService = new JwtService(properties);
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        User user = new User(
                "scout@caracasfc.com",
                "hash",
                "Manuel Silva",
                "+584121112233",
                UserRole.SCOUT,
                AccountStatus.ACTIVE
        );
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals("scout@caracasfc.com", jwtService.extractUsername(token));
        assertEquals("SCOUT", jwtService.extractRole(token));
        assertEquals(userId.toString(), jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, "scout@caracasfc.com"));
        assertFalse(jwtService.isTokenValid(token, "other@user.com"));
    }
}
