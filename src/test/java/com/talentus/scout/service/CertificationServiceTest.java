package com.talentus.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.*;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificationServiceTest {

    @Mock
    private CertificationRequestRepository certificationRequestRepository;

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private AthleteBadgeRepository athleteBadgeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TutorLegalConsentRepository tutorLegalConsentRepository;

    @Mock
    private EmailService emailService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CertificationService certificationService;

    private User tutor;
    private User admin;
    private Athlete athlete;
    private TutorLegalConsent consent;
    private UUID athleteId;

    @BeforeEach
    void setUp() {
        athleteId = UUID.randomUUID();
        tutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@talentus.app")
                .fullName("Carlos Mendoza")
                .role(UserRole.TUTOR)
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@talentus.app")
                .fullName("Super Admin")
                .role(UserRole.ADMIN)
                .build();

        athlete = Athlete.builder()
                .id(athleteId)
                .firstName("Santiago")
                .lastName("Mendoza")
                .birthDate(LocalDate.of(2010, 5, 20))
                .primaryPosition("DELANTERO_CENTRO")
                .tutor(tutor)
                .build();

        consent = TutorLegalConsent.builder()
                .id(UUID.randomUUID())
                .tutor(tutor)
                .acceptedLopnnaTerms(true)
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .build();
    }

    @Test
    @DisplayName("Debe crear solicitud por documento exitosamente")
    void requestCertificationWithDocument_Success() {
        CreateDocumentCertificationRequest request = CreateDocumentCertificationRequest.builder()
                .athleteId(athleteId)
                .badgeType(BadgeType.SPORT_ACHIEVEMENT)
                .title("Campeón Distrital Sub-15")
                .description("Diploma oficial de la Asociación de Fútbol")
                .evidenceDocumentUrl("https://storage.talentus.app/diploma.pdf")
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(tutorLegalConsentRepository.findByTutorId(tutor.getId())).thenReturn(List.of(consent));
        when(certificationRequestRepository.save(any(CertificationRequest.class)))
                .thenAnswer(invocation -> {
                    CertificationRequest cr = invocation.getArgument(0);
                    cr.setId(UUID.randomUUID());
                    return cr;
                });

        CertificationResponse response = certificationService.requestCertificationWithDocument(request, tutor.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Campeón Distrital Sub-15");
        assertThat(response.getRequestMethod()).isEqualTo("DOCUMENT_UPLOAD");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(certificationRequestRepository).save(any(CertificationRequest.class));
    }

    @Test
    @DisplayName("Debe crear solicitud con token y enviar invitación por correo")
    void requestCertificationWithToken_Success_SendsEmail() {
        CreateTokenCertificationRequest request = CreateTokenCertificationRequest.builder()
                .athleteId(athleteId)
                .badgeType(BadgeType.MEDICAL_ANTHRO)
                .title("Evaluación Antropométrica 2026")
                .targetEmail("doctor@clinica.com")
                .targetName("Dr. Rafael Peña")
                .targetOrganization("Centro Médico Deportivo")
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(tutorLegalConsentRepository.findByTutorId(tutor.getId())).thenReturn(List.of(consent));
        when(certificationRequestRepository.save(any(CertificationRequest.class)))
                .thenAnswer(invocation -> {
                    CertificationRequest cr = invocation.getArgument(0);
                    cr.setId(UUID.randomUUID());
                    return cr;
                });

        CertificationResponse response = certificationService.requestCertificationWithToken(request, tutor.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getTokenHash()).isNotBlank();
        assertThat(response.getTargetEmail()).isEqualTo("doctor@clinica.com");
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verify(emailService).sendCertificationInvite(
                eq("doctor@clinica.com"),
                eq("Dr. Rafael Peña"),
                contains("Santiago Mendoza"),
                eq("Carlos Mendoza"),
                anyString(),
                eq("Evaluación Antropométrica 2026"),
                anyString(),
                eq(14)
        );
    }

    @Test
    @DisplayName("Debe denegar solicitud si tutor no tiene consentimiento legal")
    void requestCertificationWithToken_NoConsent_ThrowsIllegalStateException() {
        CreateTokenCertificationRequest request = CreateTokenCertificationRequest.builder()
                .athleteId(athleteId)
                .badgeType(BadgeType.MEDICAL_ANTHRO)
                .title("Evaluación Antropométrica")
                .targetEmail("doctor@clinica.com")
                .targetName("Dr. Peña")
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(tutorLegalConsentRepository.findByTutorId(tutor.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> certificationService.requestCertificationWithToken(request, tutor.getEmail()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consentimiento legal");
    }

    @Test
    @DisplayName("Debe retornar datos públicos para token válido")
    void getPublicCertificationByToken_Success() {
        String token = "tok123456";
        CertificationRequest certReq = CertificationRequest.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .badgeType(BadgeType.MEDICAL_ANTHRO)
                .title("Evaluación Médica")
                .tokenHash(token)
                .tokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .status("PENDING")
                .targetEmail("doctor@clinica.com")
                .targetName("Dr. Peña")
                .build();

        when(certificationRequestRepository.findByTokenHash(token)).thenReturn(Optional.of(certReq));

        PublicTokenCertificationResponse response = certificationService.getPublicCertificationByToken(token);

        assertThat(response).isNotNull();
        assertThat(response.getAthleteFullName()).isEqualTo("Santiago Mendoza");
        assertThat(response.isExpired()).isFalse();
        assertThat(response.isAlreadyCompleted()).isFalse();
    }

    @Test
    @DisplayName("Debe lanzar excepción si el token no existe")
    void getPublicCertificationByToken_NotFound_ThrowsException() {
        when(certificationRequestRepository.findByTokenHash("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificationService.getPublicCertificationByToken("inexistente"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Debe procesar certificación mediante token, aprobarla al instante y crear la insignia")
    void submitTokenCertification_Success_CreatesBadgeAndApprovesInstantly() {
        String token = "tok-valido";
        CertificationRequest certReq = CertificationRequest.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .badgeType(BadgeType.MEDICAL_ANTHRO)
                .title("Control Biométrico")
                .targetOrganization("Clínica El Ávila")
                .tokenHash(token)
                .tokenExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .status("PENDING")
                .build();

        when(certificationRequestRepository.findByTokenHash(token)).thenReturn(Optional.of(certReq));
        when(athleteBadgeRepository.save(any(AthleteBadge.class)))
                .thenAnswer(invocation -> {
                    AthleteBadge b = invocation.getArgument(0);
                    b.setId(UUID.randomUUID());
                    return b;
                });
        when(certificationRequestRepository.save(any(CertificationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubmitTokenCertificationRequest submit = SubmitTokenCertificationRequest.builder()
                .certifierFullName("Dr. Roberto Silva")
                .certifierLicenseId("CMD-99482")
                .certifierNotes("Atleta en óptimo estado físico")
                .heightCm(BigDecimal.valueOf(175.5))
                .weightKg(BigDecimal.valueOf(64.2))
                .bodyFatPercent(BigDecimal.valueOf(11.2))
                .build();

        CertificationResponse response = certificationService.submitTokenCertification(token, submit, "192.168.1.10");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getCertifierFullName()).isEqualTo("Dr. Roberto Silva");
        assertThat(response.getBadgeId()).isNotNull();

        ArgumentCaptor<AthleteBadge> badgeCaptor = ArgumentCaptor.forClass(AthleteBadge.class);
        verify(athleteBadgeRepository).save(badgeCaptor.capture());
        AthleteBadge createdBadge = badgeCaptor.getValue();
        assertThat(createdBadge.getStatus()).isEqualTo(BadgeStatus.APPROVED);
        assertThat(createdBadge.getBadgeType()).isEqualTo(BadgeType.MEDICAL_ANTHRO);
        assertThat(createdBadge.getVerificationNotes()).contains("CMD-99482");
    }

    @Test
    @DisplayName("Debe lanzar excepción si se intenta reenviar una certificación ya aprobada")
    void submitTokenCertification_AlreadyApproved_ThrowsException() {
        String token = "tok-completado";
        CertificationRequest certReq = CertificationRequest.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .tokenHash(token)
                .status("APPROVED")
                .completedAt(Instant.now())
                .build();

        when(certificationRequestRepository.findByTokenHash(token)).thenReturn(Optional.of(certReq));

        SubmitTokenCertificationRequest submit = SubmitTokenCertificationRequest.builder()
                .certifierFullName("Dr. Silva")
                .certifierLicenseId("CMD-99482")
                .build();

        assertThatThrownBy(() -> certificationService.submitTokenCertification(token, submit, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue procesada");
    }

    @Test
    @DisplayName("Admin aprueba solicitud por documento y genera la insignia")
    void adminReviewDocument_Approve_CreatesBadge() {
        UUID reqId = UUID.randomUUID();
        CertificationRequest certReq = CertificationRequest.builder()
                .id(reqId)
                .athlete(athlete)
                .badgeType(BadgeType.SPORT_ACHIEVEMENT)
                .title("Goleador Torneo Clausura")
                .requestMethod("DOCUMENT_UPLOAD")
                .evidenceDocumentUrl("https://storage.talentus.app/doc.pdf")
                .status("PENDING")
                .build();

        when(certificationRequestRepository.findById(reqId)).thenReturn(Optional.of(certReq));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(athleteBadgeRepository.save(any(AthleteBadge.class)))
                .thenAnswer(invocation -> {
                    AthleteBadge b = invocation.getArgument(0);
                    b.setId(UUID.randomUUID());
                    return b;
                });
        when(certificationRequestRepository.save(any(CertificationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminReviewCertificationRequest review = AdminReviewCertificationRequest.builder()
                .decision("APPROVE")
                .adminNotes("Documento verificado contra acta de premiación")
                .build();

        CertificationResponse response = certificationService.adminReviewDocument(reqId, review, admin.getEmail());

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getBadgeId()).isNotNull();
        verify(athleteBadgeRepository).save(any(AthleteBadge.class));
    }
}
