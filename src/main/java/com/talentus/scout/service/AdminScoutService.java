package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.PendingScoutResponse;
import com.talentus.scout.web.dto.ReviewScoutDecision;
import com.talentus.scout.web.dto.ReviewScoutRequest;
import com.talentus.scout.web.dto.ScoutReviewResponse;
import com.talentus.scout.service.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import com.talentus.scout.service.storage.FileStorageService;

import java.time.Duration;

@Service
@Slf4j
public class AdminScoutService {

    private final ScoutProfileRepository scoutProfileRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    public AdminScoutService(
            ScoutProfileRepository scoutProfileRepository,
            UserRepository userRepository,
            EmailService emailService,
            FileStorageService fileStorageService
    ) {
        this.scoutProfileRepository = scoutProfileRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public Page<PendingScoutResponse> getPendingScouts(Pageable pageable) {
        return scoutProfileRepository.findByVerificationStatus(ScoutVerificationStatus.PENDING, pageable)
                .map(profile -> {
                    String presignedCredUrl = (profile.getCredentialsDocumentUrl() != null && !profile.getCredentialsDocumentUrl().isBlank())
                            ? fileStorageService.generatePresignedUrl(profile.getCredentialsDocumentUrl(), Duration.ofMinutes(5))
                            : null;

                    return PendingScoutResponse.builder()
                            .scoutProfileId(profile.getId())
                            .userId(profile.getUser().getId())
                            .fullName(profile.getUser().getFullName())
                            .email(profile.getUser().getEmail())
                            .phoneNumber(profile.getUser().getPhoneNumber())
                            .organizationName(profile.getOrganizationName())
                            .scoutRole(profile.getScoutRole())
                            .credentialsDocumentUrl(presignedCredUrl)
                            .linkedinUrl(profile.getLinkedinUrl())
                            .verificationStatus(profile.getVerificationStatus())
                            .createdAt(profile.getCreatedAt())
                            .build();
                });
    }

    @Transactional
    public ScoutReviewResponse reviewScout(UUID scoutProfileId, ReviewScoutRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador no encontrado con email: " + adminEmail));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Solo usuarios con rol ADMIN pueden auditar y aprobar reclutadores");
        }

        ScoutProfile scout = scoutProfileRepository.findById(scoutProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de scout no encontrado con ID: " + scoutProfileId));

        if (request.getDecision() == ReviewScoutDecision.REJECT) {
            if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
                throw new IllegalArgumentException("El motivo de rechazo es obligatorio para rechazar la solicitud");
            }
            scout.setVerificationStatus(ScoutVerificationStatus.REJECTED);
            scout.setRejectionReason(request.getRejectionReason().trim());
            scout.getUser().setStatus(AccountStatus.PENDING_VERIFICATION);
        } else if (request.getDecision() == ReviewScoutDecision.APPROVE) {
            scout.setVerificationStatus(ScoutVerificationStatus.APPROVED);
            scout.setRejectionReason(null);
            scout.getUser().setStatus(AccountStatus.ACTIVE);
        }

        scout.setReviewedByAdmin(admin);
        scout.setReviewedAt(Instant.now());

        userRepository.save(scout.getUser());
        scoutProfileRepository.save(scout);

        // Despacho de notificación oficial al scout
        try {
            boolean isApproved = (request.getDecision() == ReviewScoutDecision.APPROVE);
            emailService.sendScoutVerificationResolutionNotification(
                    scout.getUser().getEmail(),
                    scout.getUser().getFullName(),
                    isApproved,
                    request.getRejectionReason()
            );
        } catch (Exception e) {
            log.error("Error al enviar notificación por correo al scout {}: {}", scout.getUser().getEmail(), e.getMessage());
        }

        return ScoutReviewResponse.builder()
                .scoutProfileId(scout.getId())
                .userId(scout.getUser().getId())
                .fullName(scout.getUser().getFullName())
                .verificationStatus(scout.getVerificationStatus())
                .accountStatus(scout.getUser().getStatus())
                .reviewedByAdminId(admin.getId())
                .reviewedByAdminName(admin.getFullName())
                .reviewedAt(scout.getReviewedAt())
                .rejectionReason(scout.getRejectionReason())
                .message(scout.getVerificationStatus() == ScoutVerificationStatus.APPROVED
                        ? "Reclutador aprobado exitosamente y habilitado para búsqueda de talentos."
                        : "Solicitud de reclutador rechazada con motivo documentado.")
                .build();
    }
}
