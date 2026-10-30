package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.service.storage.FileStorageService;
import com.talentus.scout.web.dto.admin.club.AdminClubResponse;
import com.talentus.scout.web.dto.admin.club.ClubStatsResponse;
import com.talentus.scout.web.dto.admin.club.CreateClubRequest;
import com.talentus.scout.web.dto.admin.club.UpdateClubRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AdminClubService {

    private final ClubOrganizationRepository clubOrganizationRepository;
    private final AthleteRepository athleteRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    public AdminClubService(ClubOrganizationRepository clubOrganizationRepository,
                            AthleteRepository athleteRepository,
                            ScoutProfileRepository scoutProfileRepository,
                            EmailService emailService,
                            FileStorageService fileStorageService) {
        this.clubOrganizationRepository = clubOrganizationRepository;
        this.athleteRepository = athleteRepository;
        this.scoutProfileRepository = scoutProfileRepository;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public Page<AdminClubResponse> getClubs(String search, String status, Pageable pageable) {
        String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim().toLowerCase() : null;
        Boolean verifiedParam = null;

        if ("VERIFIED".equalsIgnoreCase(status)) {
            verifiedParam = true;
        } else if ("PENDING_VERIFICATION".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
            verifiedParam = false;
        }

        final Boolean finalVerified = verifiedParam;
        org.springframework.data.jpa.domain.Specification<ClubOrganization> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (searchParam != null) {
                String pattern = "%" + searchParam + "%";
                jakarta.persistence.criteria.Predicate namePred = cb.like(cb.lower(root.get("name")), pattern);
                jakarta.persistence.criteria.Predicate cityPred = cb.like(cb.lower(root.get("city")), pattern);
                jakarta.persistence.criteria.Predicate fedPred = cb.like(cb.lower(cb.coalesce(root.get("federationCode"), "")), pattern);
                predicates.add(cb.or(namePred, cityPred, fedPred));
            }
            if (finalVerified != null) {
                predicates.add(cb.equal(root.get("verifiedOfficial"), finalVerified));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<ClubOrganization> clubsPage = clubOrganizationRepository.findAll(spec, pageable);
        return clubsPage.map(this::toResponse);
    }


    @Transactional(readOnly = true)
    public ClubStatsResponse getStats() {
        long totalClubs = clubOrganizationRepository.count();
        long verifiedClubs = clubOrganizationRepository.countByVerifiedOfficial(true);
        long pendingClubs = clubOrganizationRepository.countByVerifiedOfficial(false);
        long totalAffiliatedAthletes = athleteRepository.countTotalAffiliatedAthletes();

        return ClubStatsResponse.builder()
                .totalClubs(totalClubs)
                .verifiedClubs(verifiedClubs)
                .pendingClubs(pendingClubs)
                .totalAffiliatedAthletes(totalAffiliatedAthletes)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminClubResponse getClubDetail(UUID id) {
        ClubOrganization club = clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));
        return toResponse(club);
    }

    @Transactional
    public AdminClubResponse createClub(CreateClubRequest request) {
        String normalizedName = request.getName().trim();
        clubOrganizationRepository.findByNameIgnoreCase(normalizedName).ifPresent(c -> {
            throw new IllegalArgumentException("Ya existe una organización registrada con el nombre: " + normalizedName);
        });

        ClubOrganization club = ClubOrganization.builder()
                .name(normalizedName)
                .federationCode(request.getFederationCode() != null && !request.getFederationCode().isBlank()
                        ? request.getFederationCode().trim() : null)
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .country(request.getCountry() != null && !request.getCountry().isBlank()
                        ? request.getCountry().trim() : "Venezuela")
                .logoUrl(request.getLogoUrl() != null && !request.getLogoUrl().isBlank()
                        ? request.getLogoUrl().trim() : null)
                .verifiedOfficial(request.isVerifiedOfficial())
                .build();

        ClubOrganization saved = clubOrganizationRepository.save(club);
        log.info("Club creado por administración: {} (ID: {})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public AdminClubResponse updateClub(UUID id, UpdateClubRequest request) {
        ClubOrganization club = clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));

        String normalizedName = request.getName().trim();
        clubOrganizationRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Ya existe otra organización con el nombre: " + normalizedName);
                });

        club.setName(normalizedName);
        club.setFederationCode(request.getFederationCode() != null && !request.getFederationCode().isBlank()
                ? request.getFederationCode().trim() : null);
        club.setCity(request.getCity().trim());
        club.setState(request.getState().trim());
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            club.setCountry(request.getCountry().trim());
        }
        if (request.getLogoUrl() != null) {
            club.setLogoUrl(request.getLogoUrl().isBlank() ? null : request.getLogoUrl().trim());
        }
        if (request.getVerifiedOfficial() != null) {
            club.setVerifiedOfficial(request.getVerifiedOfficial());
        }

        ClubOrganization updated = clubOrganizationRepository.save(club);
        log.info("Club actualizado por administración: {} (ID: {})", updated.getName(), updated.getId());
        return toResponse(updated);
    }

    @Transactional
    public AdminClubResponse toggleVerification(UUID id, Boolean verifiedOfficial) {
        ClubOrganization club = clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));

        boolean newStatus = (verifiedOfficial != null) ? verifiedOfficial : !club.isVerifiedOfficial();
        club.setVerifiedOfficial(newStatus);
        ClubOrganization updated = clubOrganizationRepository.save(club);
        log.info("Estado de verificación de club {} cambiado a {}", updated.getName(), newStatus);
        return toResponse(updated);
    }

    @Transactional
    public String uploadLogo(UUID id, MultipartFile file) {
        ClubOrganization club = clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));

        String previousLogo = club.getLogoUrl();
        String storedUrl = fileStorageService.storeFile(file, "clubs");
        club.setLogoUrl(storedUrl);
        clubOrganizationRepository.save(club);

        if (previousLogo != null && previousLogo.startsWith("/uploads/clubs/")) {
            fileStorageService.deleteFile(previousLogo);
        }

        log.info("Nuevo escudo almacenado para club {}: {}", club.getName(), storedUrl);
        return storedUrl;
    }

    @Transactional
    public void deleteClub(UUID id) {
        ClubOrganization club = clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));

        String clubName = club.getName();

        // 1. Desvincular atletas asociados (dejándolos como independientes con currentClub = null)
        long athletesCount = athleteRepository.countByCurrentClubId(id);
        if (athletesCount > 0) {
            athleteRepository.detachClubFromAthletes(id);
            log.info("Desvinculados {} atletas del club '{}' ({})", athletesCount, clubName, id);
        }

        // 2. Gestionar scouts asociados (cambiar a REJECTED con motivo de subsanación y notificar por email)
        List<ScoutProfile> associatedScouts = scoutProfileRepository.findByOrganizationNameIgnoreCase(clubName);
        for (ScoutProfile scout : associatedScouts) {
            scout.setVerificationStatus(ScoutVerificationStatus.REJECTED);
            String reason = "Tu organización deportiva '" + clubName + "' ha sido dada de baja por la administración. Por favor actualiza tu organización y credencial deportiva vigente para reactivar tu cuenta.";
            scout.setRejectionReason(reason);
            scout.setReviewedAt(Instant.now());
            scoutProfileRepository.save(scout);

            if (scout.getUser() != null && scout.getUser().getEmail() != null) {
                emailService.sendScoutClubRevocationNotification(
                        scout.getUser().getEmail(),
                        scout.getUser().getFullName(),
                        clubName,
                        reason
                );
            }
        }
        log.info("Scouts revocados y notificados por baja del club '{}': {}", clubName, associatedScouts.size());

        // 3. Eliminar club y su archivo físico de escudo
        String logoUrl = club.getLogoUrl();
        clubOrganizationRepository.delete(club);

        if (logoUrl != null && logoUrl.startsWith("/uploads/clubs/")) {
            fileStorageService.deleteFile(logoUrl);
        }

        log.info("Club '{}' ({}) eliminado definitivamente del sistema.", clubName, id);
    }

    private AdminClubResponse toResponse(ClubOrganization club) {
        long athletesCount = athleteRepository.countByCurrentClubId(club.getId());
        long scoutsCount = scoutProfileRepository.countByOrganizationNameIgnoreCase(club.getName());

        return AdminClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .federationCode(club.getFederationCode())
                .city(club.getCity())
                .state(club.getState())
                .country(club.getCountry())
                .logoUrl(club.getLogoUrl())
                .verifiedOfficial(club.isVerifiedOfficial())
                .createdAt(club.getCreatedAt())
                .athletesCount(athletesCount)
                .scoutsCount(scoutsCount)
                .build();
    }
}
