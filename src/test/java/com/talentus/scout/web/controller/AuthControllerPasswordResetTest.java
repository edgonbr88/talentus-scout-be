package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.PasswordResetToken;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.PasswordResetTokenRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.ForgotPasswordRequest;
import com.talentus.scout.web.dto.ResetPasswordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthControllerPasswordResetTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private User testUser;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String uniqueEmail = "reset.test." + UUID.randomUUID() + "@talentus.app";
        testUser = User.builder()
                .email(uniqueEmail)
                .passwordHash(passwordEncoder.encode("InitialPassword123*"))
                .fullName("Usuario Prueba Recuperacion")
                .phoneNumber("+584121234567")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password con correo existente retorna 200 y mensaje genérico")
    void forgotPassword_withExistingEmail_returnsOk() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(testUser.getEmail())
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si el correo electrónico está registrado en Talentus Scout, recibirás un enlace de restablecimiento en los próximos minutos."));

        assertThat(passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(testUser)).isPresent();
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password con correo inexistente retorna 200 y mensaje genérico (anti-enumeración)")
    void forgotPassword_withNonExistingEmail_returnsOkGeneric() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("inexistente.total." + UUID.randomUUID() + "@talentus.app")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si el correo electrónico está registrado en Talentus Scout, recibirás un enlace de restablecimiento en los próximos minutos."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password con token válido actualiza contraseña y permite login")
    void resetPassword_withValidToken_updatesPasswordSuccessfully() throws Exception {
        String tokenStr = "it-token-" + UUID.randomUUID();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .token(tokenStr)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();
        passwordResetTokenRepository.save(resetToken);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(tokenStr)
                .newPassword("BrandNewSecurePassword2026*")
                .confirmPassword("BrandNewSecurePassword2026*")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tu contraseña ha sido actualizada exitosamente. Ya puedes iniciar sesión con tus nuevas credenciales."));

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewSecurePassword2026*", updatedUser.getPasswordHash())).isTrue();

        PasswordResetToken updatedToken = passwordResetTokenRepository.findByToken(tokenStr).orElseThrow();
        assertThat(updatedToken.isUsed()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password con contraseñas que no coinciden retorna 400")
    void resetPassword_withMismatchedPasswords_returnsBadRequest() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("any-token")
                .newPassword("PasswordA2026*")
                .confirmPassword("PasswordB2026*")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La nueva contraseña y la confirmación no coinciden"));
    }
}
