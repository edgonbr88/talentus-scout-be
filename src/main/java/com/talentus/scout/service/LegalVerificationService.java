package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.legal.LegalVerificationPendingItemResponse;
import com.talentus.scout.web.dto.legal.LegalVerificationReviewResponse;
import com.talentus.scout.web.dto.legal.ReviewLegalVerificationRequest;
import com.talentus.scout.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LegalVerificationService {

    private final AthleteRepository athleteRepository;
    private final TutorLegalConsentRepository tutorLegalConsentRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public Page<LegalVerificationPendingItemResponse> getPendingVerifications(Pageable pageable) {
        Page<Athlete> page = athleteRepository.findByLegalVerificationStatusAndIdentityDocumentFileUrlIsNotNullOrderByUpdatedAtDesc(
                LegalVerificationStatus.PENDING_REVIEW,
                pageable
        );
        return page.map(this::mapToItemResponse);
    }

    @Transactional(readOnly = true)
    public LegalVerificationPendingItemResponse getVerificationDetail(UUID athleteId) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + athleteId));
        return mapToItemResponse(athlete);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVerificationMetrics() {
        long pending = athleteRepository.countByLegalVerificationStatusAndIdentityDocumentFileUrlIsNotNull(LegalVerificationStatus.PENDING_REVIEW);
        long approved = athleteRepository.countByLegalVerificationStatus(LegalVerificationStatus.APPROVED);
        long rejected = athleteRepository.countByLegalVerificationStatus(LegalVerificationStatus.REJECTED);

        return Map.of(
                "pendingCount", pending,
                "approvedCount", approved,
                "rejectedCount", rejected
        );
    }

    @Transactional
    public LegalVerificationReviewResponse reviewVerification(UUID athleteId, ReviewLegalVerificationRequest request, String adminEmail) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + athleteId));

        if (request.getDecision() == LegalVerificationStatus.REJECTED) {
            if (request.getNotes() == null || request.getNotes().trim().isEmpty()) {
                throw new IllegalArgumentException("El motivo de observación o rechazo es obligatorio para notificar al representante legal.");
            }
        }

        User adminUser = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador no encontrado con email: " + adminEmail));

        athlete.setLegalVerificationStatus(request.getDecision());
        athlete.setLegalVerificationNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        athlete.setLegalVerifiedBy(adminUser);
        athlete.setLegalVerifiedAt(Instant.now());

        Athlete updated = athleteRepository.save(athlete);

        // Despacho de notificación al tutor
        User tutor = updated.getTutor();
        if (tutor != null && tutor.getEmail() != null) {
            String athleteFullName = updated.getFirstName() + " " + updated.getLastName();
            boolean isApproved = (request.getDecision() == LegalVerificationStatus.APPROVED);
            try {
                emailService.sendLegalVerificationResolutionNotification(
                        tutor.getEmail(),
                        tutor.getFullName(),
                        athleteFullName,
                        isApproved,
                        request.getNotes()
                );
            } catch (Exception e) {
                log.error("Error al despachar notificación de auditoría legal a {}: {}", tutor.getEmail(), e.getMessage());
            }
        }

        String msg = (request.getDecision() == LegalVerificationStatus.APPROVED)
                ? "Patria potestad y representación legal aprobada con éxito."
                : "Solicitud de patria potestad observada y notificada al representante legal.";

        return LegalVerificationReviewResponse.builder()
                .athleteId(updated.getId())
                .decision(updated.getLegalVerificationStatus())
                .notes(updated.getLegalVerificationNotes())
                .verifiedByEmail(adminUser.getEmail())
                .verifiedAt(updated.getLegalVerifiedAt())
                .message(msg)
                .build();
    }

    private LegalVerificationPendingItemResponse mapToItemResponse(Athlete athlete) {
        User tutor = athlete.getTutor();
        TutorLegalConsent bestConsent = null;
        if (tutor != null) {
            List<TutorLegalConsent> consents = tutorLegalConsentRepository.findByTutorId(tutor.getId());
            if (consents != null && !consents.isEmpty()) {
                bestConsent = consents.stream()
                        .filter(c -> c.getIdDocumentFileUrl() != null && !c.getIdDocumentFileUrl().isBlank())
                        .max(Comparator.comparing(TutorLegalConsent::getAcceptedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(consents.get(0));
            }
        }

        Integer age = null;
        if (athlete.getBirthDate() != null) {
            age = Period.between(athlete.getBirthDate(), LocalDate.now()).getYears();
        }

        String athleteDocUrl = (athlete.getIdentityDocumentFileUrl() != null && !athlete.getIdentityDocumentFileUrl().isBlank())
                ? fileStorageService.generatePresignedUrl(athlete.getIdentityDocumentFileUrl(), Duration.ofMinutes(5))
                : null;
        String tutorDocUrl = (bestConsent != null && bestConsent.getIdDocumentFileUrl() != null && !bestConsent.getIdDocumentFileUrl().isBlank())
                ? fileStorageService.generatePresignedUrl(bestConsent.getIdDocumentFileUrl(), Duration.ofMinutes(5))
                : null;

        return LegalVerificationPendingItemResponse.builder()
                .athleteId(athlete.getId())
                .athleteFirstName(athlete.getFirstName())
                .athleteLastName(athlete.getLastName())
                .athleteFullName(athlete.getFirstName() + " " + athlete.getLastName())
                .athleteSlug(athlete.getSlug())
                .birthDate(athlete.getBirthDate())
                .athleteAge(age)
                .primaryPosition(athlete.getPrimaryPosition())
                .nationality(athlete.getNationality())
                .athleteDocType(athlete.getIdentityDocumentType())
                .athleteDocNumber(athlete.getIdentityDocumentNumber())
                .athleteDocFileUrl(athleteDocUrl)
                .tutorId(tutor != null ? tutor.getId() : null)
                .tutorFullName(tutor != null ? tutor.getFullName() : null)
                .tutorEmail(tutor != null ? tutor.getEmail() : null)
                .tutorPhoneNumber(tutor != null ? tutor.getPhoneNumber() : null)
                .tutorDocType(bestConsent != null ? bestConsent.getIdDocumentType() : null)
                .tutorDocNumber(bestConsent != null ? bestConsent.getIdDocumentNumber() : null)
                .tutorDocFileUrl(tutorDocUrl)
                .submittedAt(athlete.getUpdatedAt() != null ? athlete.getUpdatedAt() : athlete.getCreatedAt())
                .status(athlete.getLegalVerificationStatus())
                .notes(athlete.getLegalVerificationNotes())
                .verifiedAt(athlete.getLegalVerifiedAt())
                .verifiedByEmail(athlete.getLegalVerifiedBy() != null ? athlete.getLegalVerifiedBy().getEmail() : null)
                .build();
    }
}
