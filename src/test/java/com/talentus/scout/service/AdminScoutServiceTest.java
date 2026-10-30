package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.ReviewScoutDecision;
import com.talentus.scout.web.dto.ReviewScoutRequest;
import com.talentus.scout.web.dto.ScoutReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminScoutServiceTest {

    @Mock
    private ScoutProfileRepository scoutProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private com.talentus.scout.service.storage.FileStorageService fileStorageService;

    @InjectMocks
    private AdminScoutService adminScoutService;

    private User adminUser;
    private User scoutUser;
    private ScoutProfile scoutProfile;
    private UUID scoutProfileId;

    @BeforeEach
    void setUp() {
        scoutProfileId = UUID.randomUUID();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@talentus.app")
                .fullName("Administrador Oficial")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();

        scoutUser = User.builder()
                .id(UUID.randomUUID())
                .email("scout@club.com")
                .fullName("Carlos Reclutador")
                .role(UserRole.SCOUT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();

        scoutProfile = ScoutProfile.builder()
                .id(scoutProfileId)
                .user(scoutUser)
                .organizationName("Caracas FC")
                .scoutRole("Reclutador Oficial")
                .credentialsDocumentUrl("/uploads/credentials/cred.pdf")
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Debe aprobar scout y despachar correo de bienvenida con credenciales activas")
    void reviewScout_aprobacion_debeEnviarCorreoExitoso() {
        when(userRepository.findByEmail("admin@talentus.app")).thenReturn(Optional.of(adminUser));
        when(scoutProfileRepository.findById(scoutProfileId)).thenReturn(Optional.of(scoutProfile));

        ReviewScoutRequest request = new ReviewScoutRequest();
        request.setDecision(ReviewScoutDecision.APPROVE);

        ScoutReviewResponse response = adminScoutService.reviewScout(scoutProfileId, request, "admin@talentus.app");

        assertThat(response.getVerificationStatus()).isEqualTo(ScoutVerificationStatus.APPROVED);
        assertThat(scoutUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        verify(emailService, times(1)).sendScoutVerificationResolutionNotification(
                eq("scout@club.com"),
                eq("Carlos Reclutador"),
                eq(true),
                isNull()
        );
    }

    @Test
    @DisplayName("Debe rechazar scout y despachar correo con motivo de observación")
    void reviewScout_rechazo_debeEnviarCorreoConMotivo() {
        when(userRepository.findByEmail("admin@talentus.app")).thenReturn(Optional.of(adminUser));
        when(scoutProfileRepository.findById(scoutProfileId)).thenReturn(Optional.of(scoutProfile));

        ReviewScoutRequest request = new ReviewScoutRequest();
        request.setDecision(ReviewScoutDecision.REJECT);
        request.setRejectionReason("Carnet federativo no legible");

        ScoutReviewResponse response = adminScoutService.reviewScout(scoutProfileId, request, "admin@talentus.app");

        assertThat(response.getVerificationStatus()).isEqualTo(ScoutVerificationStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Carnet federativo no legible");

        verify(emailService, times(1)).sendScoutVerificationResolutionNotification(
                eq("scout@club.com"),
                eq("Carlos Reclutador"),
                eq(false),
                eq("Carnet federativo no legible")
        );
    }
}
