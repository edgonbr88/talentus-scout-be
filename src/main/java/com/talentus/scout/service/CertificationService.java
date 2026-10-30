package com.talentus.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.entity.CertificationRequest;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.CertificationRequestRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificationService {

    private final CertificationRequestRepository certificationRequestRepository;
    private final AthleteRepository athleteRepository;
    private final AthleteBadgeRepository athleteBadgeRepository;
    private final UserRepository userRepository;
    private final TutorLegalConsentRepository tutorLegalConsentRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${talentus.frontend.certify-url:http://localhost:4200/certify}")
    private String certifyBaseUrl;

    @Transactional
    public CertificationResponse requestCertificationWithDocument(CreateDocumentCertificationRequest request, String tutorEmail) {
        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + request.getAthleteId()));

        User user = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + tutorEmail));

        validateTutorAuthority(athlete, user);

        CertificationRequest certReq = CertificationRequest.builder()
                .athlete(athlete)
                .badgeType(request.getBadgeType())
                .requestMethod("DOCUMENT_UPLOAD")
                .status("PENDING")
                .title(request.getTitle())
                .description(request.getDescription())
                .evidenceDocumentUrl(request.getEvidenceDocumentUrl())
                .build();

        CertificationRequest saved = certificationRequestRepository.save(certReq);
        log.info("Solicitud de certificación por documento creada con ID {}", saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public CertificationResponse requestCertificationWithToken(CreateTokenCertificationRequest request, String tutorEmail) {
        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + request.getAthleteId()));

        User user = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + tutorEmail));

        validateTutorAuthority(athlete, user);

        String token = UUID.randomUUID().toString().replace("-", "") + Long.toHexString(System.currentTimeMillis());
        Instant expiresAt = Instant.now().plus(14, ChronoUnit.DAYS);

        CertificationRequest certReq = CertificationRequest.builder()
                .athlete(athlete)
                .badgeType(request.getBadgeType())
                .requestMethod("TOKEN_LINK")
                .status("PENDING")
                .title(request.getTitle())
                .description(request.getDescription())
                .targetEmail(request.getTargetEmail().trim().toLowerCase())
                .targetName(request.getTargetName().trim())
                .targetOrganization(request.getTargetOrganization() != null ? request.getTargetOrganization().trim() : null)
                .tokenHash(token)
                .tokenExpiresAt(expiresAt)
                .build();

        CertificationRequest saved = certificationRequestRepository.save(certReq);

        String certLink = certifyBaseUrl + "/" + token;
        String tutorName = athlete.getTutor() != null ? athlete.getTutor().getFullName() : "Representante Legal";
        String athleteFullName = athlete.getFirstName() + " " + athlete.getLastName();
        String badgeLabel = resolveBadgeLabel(request.getBadgeType());

        emailService.sendCertificationInvite(
                request.getTargetEmail(),
                request.getTargetName(),
                athleteFullName,
                tutorName,
                badgeLabel,
                request.getTitle(),
                certLink,
                14
        );

        log.info("Solicitud de certificación con token generada con éxito para atleta {} (Token: {})", athlete.getId(), token);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PublicTokenCertificationResponse getPublicCertificationByToken(String token) {
        CertificationRequest certReq = certificationRequestRepository.findByTokenHash(token)
                .orElseThrow(() -> new ResourceNotFoundException("El enlace o token de certificación no es válido o no existe"));

        Athlete athlete = certReq.getAthlete();
        boolean isExpired = certReq.getTokenExpiresAt() != null && Instant.now().isAfter(certReq.getTokenExpiresAt());
        boolean isCompleted = "APPROVED".equalsIgnoreCase(certReq.getStatus()) || certReq.getCompletedAt() != null;

        int birthYear = athlete.getBirthDate() != null ? athlete.getBirthDate().getYear() : 0;
        String category = birthYear > 0 ? "Categoría " + birthYear : "Fútbol Juvenil";
        String clubName = athlete.getCurrentClub() != null ? athlete.getCurrentClub().getName() : "Agente Libre";

        return PublicTokenCertificationResponse.builder()
                .token(token)
                .athleteId(athlete.getId())
                .athleteFullName(athlete.getFirstName() + " " + athlete.getLastName())
                .athleteCategory(category)
                .athleteClubName(clubName)
                .athletePosition(athlete.getPrimaryPosition())
                .badgeType(certReq.getBadgeType())
                .badgeTypeLabel(resolveBadgeLabel(certReq.getBadgeType()))
                .title(certReq.getTitle())
                .description(certReq.getDescription())
                .targetEmail(certReq.getTargetEmail())
                .targetName(certReq.getTargetName())
                .targetOrganization(certReq.getTargetOrganization())
                .expiresAt(certReq.getTokenExpiresAt())
                .expired(isExpired)
                .alreadyCompleted(isCompleted)
                .build();
    }

    @Transactional
    public CertificationResponse submitTokenCertification(String token, SubmitTokenCertificationRequest submitRequest, String clientIp) {
        CertificationRequest certReq = certificationRequestRepository.findByTokenHash(token)
                .orElseThrow(() -> new ResourceNotFoundException("El token de certificación no existe"));

        if ("APPROVED".equalsIgnoreCase(certReq.getStatus()) || certReq.getCompletedAt() != null) {
            throw new IllegalStateException("Esta solicitud de certificación ya fue procesada y avalada con anterioridad.");
        }

        if (certReq.getTokenExpiresAt() != null && Instant.now().isAfter(certReq.getTokenExpiresAt())) {
            throw new IllegalStateException("El enlace de certificación ha expirado. Por favor solicite un nuevo enlace al representante.");
        }

        // Construir datos en JSON
        Map<String, Object> dataMap = new LinkedHashMap<>();
        if (submitRequest.getHeightCm() != null) dataMap.put("heightCm", submitRequest.getHeightCm());
        if (submitRequest.getWeightKg() != null) dataMap.put("weightKg", submitRequest.getWeightKg());
        if (submitRequest.getBodyFatPercent() != null) dataMap.put("bodyFatPercent", submitRequest.getBodyFatPercent());
        if (submitRequest.getMuscleMassKg() != null) dataMap.put("muscleMassKg", submitRequest.getMuscleMassKg());
        if (submitRequest.getGoalsScored() != null) dataMap.put("goalsScored", submitRequest.getGoalsScored());
        if (submitRequest.getTournamentName() != null) dataMap.put("tournamentName", submitRequest.getTournamentName());
        if (submitRequest.getAchievementTitle() != null) dataMap.put("achievementTitle", submitRequest.getAchievementTitle());

        String jsonPayload = null;
        try {
            jsonPayload = objectMapper.writeValueAsString(dataMap);
        } catch (Exception e) {
            log.warn("Error al serializar datos de certificación: {}", e.getMessage());
        }

        certReq.setCertifierFullName(submitRequest.getCertifierFullName().trim());
        certReq.setCertifierLicenseId(submitRequest.getCertifierLicenseId().trim());
        certReq.setCertifierNotes(submitRequest.getCertifierNotes());
        certReq.setCertifierIp(clientIp);
        certReq.setCertificationDataJson(jsonPayload);
        certReq.setCompletedAt(Instant.now());
        certReq.setStatus("APPROVED");

        // Crear inmediatamente la insignia aprobada (Opción A: Aprobación instantánea con auditoría)
        String notes = certReq.getTitle() + " — Avalado por " + submitRequest.getCertifierFullName() +
                " (" + submitRequest.getCertifierLicenseId() + ")" +
                (certReq.getTargetOrganization() != null ? " [" + certReq.getTargetOrganization() + "]" : "");

        AthleteBadge badge = AthleteBadge.builder()
                .athlete(certReq.getAthlete())
                .badgeType(certReq.getBadgeType())
                .status(BadgeStatus.APPROVED)
                .verificationNotes(notes)
                .verifiedAt(Instant.now())
                .build();

        AthleteBadge savedBadge = athleteBadgeRepository.save(badge);
        certReq.setBadge(savedBadge);

        CertificationRequest updatedReq = certificationRequestRepository.save(certReq);
        log.info("Certificación mediante token completada exitosamente. Insignia ID {} generada.", savedBadge.getId());

        // Notificación al tutor legal del atleta
        try {
            Athlete athlete = certReq.getAthlete();
            if (athlete != null && athlete.getTutor() != null && athlete.getTutor().getEmail() != null) {
                String athleteName = athlete.getFirstName() + " " + athlete.getLastName();
                emailService.sendBadgeCertificationResolutionNotification(
                        athlete.getTutor().getEmail(),
                        athlete.getTutor().getFullName(),
                        athleteName,
                        certReq.getTitle(),
                        resolveBadgeLabel(certReq.getBadgeType()),
                        true,
                        null
                );
            }
        } catch (Exception e) {
            log.error("Error al enviar notificación de insignia avalada: {}", e.getMessage());
        }

        return mapToResponse(updatedReq);
    }

    @Transactional
    public CertificationResponse adminReviewDocument(UUID requestId, AdminReviewCertificationRequest reviewRequest, String adminEmail) {
        CertificationRequest certReq = certificationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de certificación no encontrada con ID: " + requestId));

        User adminUser = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador no encontrado"));

        if (!adminUser.getRole().equals(UserRole.ADMIN)) {
            throw new AccessDeniedException("Solo los administradores pueden auditar documentos de certificación.");
        }

        if ("APPROVE".equalsIgnoreCase(reviewRequest.getDecision())) {
            certReq.setStatus("APPROVED");
            certReq.setCompletedAt(Instant.now());
            certReq.setReviewedByAdmin(adminUser);
            certReq.setCertifierNotes(reviewRequest.getAdminNotes());

            String notes = certReq.getTitle() + " — Verificado por Administración Talentus Scout" +
                    (reviewRequest.getAdminNotes() != null ? " (" + reviewRequest.getAdminNotes() + ")" : "");

            AthleteBadge badge = AthleteBadge.builder()
                .athlete(certReq.getAthlete())
                .badgeType(certReq.getBadgeType())
                .status(BadgeStatus.APPROVED)
                .evidenceDocumentUrl(certReq.getEvidenceDocumentUrl())
                .verificationNotes(notes)
                .verifiedByUser(adminUser)
                .verifiedAt(Instant.now())
                .build();

            AthleteBadge savedBadge = athleteBadgeRepository.save(badge);
            certReq.setBadge(savedBadge);
        } else {
            certReq.setStatus("REJECTED");
            certReq.setCompletedAt(Instant.now());
            certReq.setReviewedByAdmin(adminUser);
            certReq.setCertifierNotes(reviewRequest.getAdminNotes());
        }

        CertificationRequest saved = certificationRequestRepository.save(certReq);

        // Notificación al tutor legal del atleta sobre la resolución de insignia
        try {
            Athlete athlete = certReq.getAthlete();
            if (athlete != null && athlete.getTutor() != null && athlete.getTutor().getEmail() != null) {
                String athleteName = athlete.getFirstName() + " " + athlete.getLastName();
                boolean isApproved = "APPROVE".equalsIgnoreCase(reviewRequest.getDecision());
                emailService.sendBadgeCertificationResolutionNotification(
                        athlete.getTutor().getEmail(),
                        athlete.getTutor().getFullName(),
                        athleteName,
                        certReq.getTitle(),
                        resolveBadgeLabel(certReq.getBadgeType()),
                        isApproved,
                        reviewRequest.getAdminNotes()
                );
            }
        } catch (Exception e) {
            log.error("Error al enviar notificación de resolución de insignia por admin: {}", e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> getAthleteCertifications(UUID athleteId) {
        return certificationRequestRepository.findByAthleteIdOrderByCreatedAtDesc(athleteId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> getPendingAdminCertifications() {
        return certificationRequestRepository.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                .filter(req -> "DOCUMENT_UPLOAD".equalsIgnoreCase(req.getRequestMethod()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateTutorAuthority(Athlete athlete, User user) {
        if (user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        if (athlete.getTutor() == null || !athlete.getTutor().getId().equals(user.getId())) {
            throw new AccessDeniedException("No tienes autorización para gestionar certificaciones de este atleta.");
        }

        List<TutorLegalConsent> consents = tutorLegalConsentRepository.findByTutorId(user.getId());
        if (consents.isEmpty() || !consents.get(0).isAcceptedLopnnaTerms()) {
            throw new IllegalStateException("Debes tener el consentimiento legal activo para solicitar certificaciones de un atleta menor.");
        }
    }

    private String resolveBadgeLabel(BadgeType type) {
        if (type == null) return "Certificación Oficial";
        return switch (type) {
            case MEDICAL_ANTHRO -> "Evaluación Médica y Antropométrica";
            case FEDERATION_LICENSE -> "Ficha o Licencia Federativa";
            case CLUB_AFFILIATION -> "Afiliación a Club o Academia";
            case SCOUT_ENDORSEMENT -> "Aval de Scout Acreditado";
            case SPORT_ACHIEVEMENT -> "Logro o Palmarés Deportivo";
        };
    }

    private CertificationResponse mapToResponse(CertificationRequest req) {
        String link = req.getTokenHash() != null ? certifyBaseUrl + "/" + req.getTokenHash() : null;
        String athleteName = req.getAthlete() != null ? req.getAthlete().getFirstName() + " " + req.getAthlete().getLastName() : null;
        UUID athleteId = req.getAthlete() != null ? req.getAthlete().getId() : null;
        UUID badgeId = req.getBadge() != null ? req.getBadge().getId() : null;

        return CertificationResponse.builder()
                .id(req.getId())
                .athleteId(athleteId)
                .athleteFullName(athleteName)
                .badgeType(req.getBadgeType())
                .requestMethod(req.getRequestMethod())
                .status(req.getStatus())
                .title(req.getTitle())
                .description(req.getDescription())
                .evidenceDocumentUrl(req.getEvidenceDocumentUrl())
                .targetEmail(req.getTargetEmail())
                .targetName(req.getTargetName())
                .targetOrganization(req.getTargetOrganization())
                .tokenHash(req.getTokenHash())
                .certificationLink(link)
                .tokenExpiresAt(req.getTokenExpiresAt())
                .certifierFullName(req.getCertifierFullName())
                .certifierLicenseId(req.getCertifierLicenseId())
                .certifierNotes(req.getCertifierNotes())
                .certificationDataJson(req.getCertificationDataJson())
                .completedAt(req.getCompletedAt())
                .createdAt(req.getCreatedAt())
                .badgeId(badgeId)
                .build();
    }
}
