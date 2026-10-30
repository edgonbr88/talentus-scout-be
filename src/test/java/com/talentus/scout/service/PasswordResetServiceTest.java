package com.talentus.scout.service;

import com.talentus.scout.domain.entity.PasswordResetToken;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.InvalidTokenException;
import com.talentus.scout.repository.PasswordResetTokenRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.ForgotPasswordRequest;
import com.talentus.scout.web.dto.ForgotPasswordResponse;
import com.talentus.scout.web.dto.ResetPasswordRequest;
import com.talentus.scout.web.dto.ResetPasswordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "frontendBaseUrl", "http://localhost:4200");

        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@example.com")
                .fullName("Carlos Mendoza")
                .passwordHash("old_hashed_password")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("requestPasswordReset: cuando el usuario existe, elimina tokens anteriores, persiste token nuevo y envía correo")
    void requestPasswordReset_whenUserExists_sendsEmailAndReturnsGenericMessage() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("tutor@example.com")
                .build();

        when(userRepository.findByEmail("tutor@example.com")).thenReturn(Optional.of(sampleUser));

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Si el correo electrónico está registrado");

        verify(passwordResetTokenRepository).deleteByUser(sampleUser);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser()).isEqualTo(sampleUser);
        assertThat(savedToken.getToken()).isNotBlank();
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
        assertThat(savedToken.getUsedAt()).isNull();

        verify(emailService).sendPasswordResetEmail(
                eq("tutor@example.com"),
                eq("Carlos Mendoza"),
                contains("http://localhost:4200/auth/reset-password?token=" + savedToken.getToken())
        );
    }

    @Test
    @DisplayName("requestPasswordReset: cuando el usuario no existe, devuelve el mismo mensaje sin enviar correo (anti-enumeración)")
    void requestPasswordReset_whenUserDoesNotExist_returnsGenericMessageWithoutSendingEmail() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("desconocido@example.com")
                .build();

        when(userRepository.findByEmail("desconocido@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Si el correo electrónico está registrado");

        verify(passwordResetTokenRepository, never()).deleteByUser(any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resetPassword: con token válido y contraseñas coincidentes, actualiza clave y marca token usado")
    void resetPassword_whenTokenValid_updatesPasswordAndMarksUsed() {
        String tokenStr = "valid-reset-token-12345";
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .token(tokenStr)
                .expiresAt(Instant.now().plus(25, ChronoUnit.MINUTES))
                .usedAt(null)
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(tokenStr)
                .newPassword("NuevaClaveSegura2026*")
                .confirmPassword("NuevaClaveSegura2026*")
                .build();

        when(passwordResetTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NuevaClaveSegura2026*")).thenReturn("encoded_new_password");

        ResetPasswordResponse response = passwordResetService.resetPassword(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Tu contraseña ha sido actualizada exitosamente");

        assertThat(sampleUser.getPasswordHash()).isEqualTo("encoded_new_password");
        verify(userRepository).save(sampleUser);

        assertThat(resetToken.getUsedAt()).isNotNull();
        verify(passwordResetTokenRepository).save(resetToken);
    }

    @Test
    @DisplayName("resetPassword: si las contraseñas no coinciden, lanza IllegalArgumentException")
    void resetPassword_whenPasswordsDoNotMatch_throwsIllegalArgumentException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("any-token")
                .newPassword("ClaveA123*")
                .confirmPassword("ClaveB123*")
                .build();

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coinciden");

        verify(passwordResetTokenRepository, never()).findByToken(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: si el token no existe, lanza InvalidTokenException")
    void resetPassword_whenTokenNotFound_throwsInvalidTokenException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("non-existent-token")
                .newPassword("ClaveSegura2026*")
                .confirmPassword("ClaveSegura2026*")
                .build();

        when(passwordResetTokenRepository.findByToken("non-existent-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("no es válido o no existe");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: si el token ya expiró, lanza InvalidTokenException")
    void resetPassword_whenTokenExpired_throwsInvalidTokenException() {
        String tokenStr = "expired-token";
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .token(tokenStr)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .usedAt(null)
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(tokenStr)
                .newPassword("ClaveSegura2026*")
                .confirmPassword("ClaveSegura2026*")
                .build();

        when(passwordResetTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("ha expirado");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: si el token ya fue utilizado, lanza InvalidTokenException")
    void resetPassword_whenTokenAlreadyUsed_throwsInvalidTokenException() {
        String tokenStr = "already-used-token";
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .token(tokenStr)
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .usedAt(Instant.now().minus(2, ChronoUnit.MINUTES))
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(tokenStr)
                .newPassword("ClaveSegura2026*")
                .confirmPassword("ClaveSegura2026*")
                .build();

        when(passwordResetTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("ya ha sido utilizado");

        verify(userRepository, never()).save(any());
    }
}
