package com.talentus.scout.service;

import com.talentus.scout.domain.entity.PasswordResetToken;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.exception.InvalidTokenException;
import com.talentus.scout.repository.PasswordResetTokenRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.ForgotPasswordRequest;
import com.talentus.scout.web.dto.ForgotPasswordResponse;
import com.talentus.scout.web.dto.ResetPasswordRequest;
import com.talentus.scout.web.dto.ResetPasswordResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${talentus.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            passwordResetTokenRepository.deleteByUser(user);

            String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .build();

            passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendBaseUrl + "/auth/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
            log.info("Token de restablecimiento de contraseña generado y despachado para: {}", email);
        } else {
            log.info("Solicitud de recuperación de contraseña para correo no existente (anti-enumeración): {}", email);
        }

        return ForgotPasswordResponse.builder()
                .message("Si el correo electrónico está registrado en Talentus Scout, recibirás un enlace de restablecimiento en los próximos minutos.")
                .build();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("La nueva contraseña y la confirmación no coinciden");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("El enlace de restablecimiento no es válido o no existe"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Este enlace de restablecimiento ya ha sido utilizado");
        }

        if (resetToken.isExpired()) {
            throw new InvalidTokenException("El enlace de restablecimiento ha expirado. Por favor solicita uno nuevo");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        log.info("Contraseña actualizada exitosamente para el usuario ID: {}", user.getId());

        return ResetPasswordResponse.builder()
                .message("Tu contraseña ha sido actualizada exitosamente. Ya puedes iniciar sesión con tus nuevas credenciales.")
                .build();
    }
}
