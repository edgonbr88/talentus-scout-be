package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteVideoRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.AthleteResponse;
import com.talentus.scout.web.dto.CreateAthleteRequest;
import com.talentus.scout.web.dto.UpdateAthleteRequest;
import com.talentus.scout.web.dto.publicprofile.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AthleteService {

    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final ClubService clubService;
    private final SlugService slugService;
    private final AthleteVideoRepository athleteVideoRepository;
    private final AthleteBadgeRepository athleteBadgeRepository;
    private final com.talentus.scout.repository.AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final com.talentus.scout.repository.PaymentTransactionRepository paymentTransactionRepository;
    private final com.talentus.scout.service.storage.FileStorageService fileStorageService;
    private final AdminNotificationService adminNotificationService;

    public AthleteService(
            AthleteRepository athleteRepository,
            UserRepository userRepository,
            ClubService clubService,
            SlugService slugService,
            AthleteVideoRepository athleteVideoRepository,
            AthleteBadgeRepository athleteBadgeRepository,
            com.talentus.scout.repository.AthleteSubscriptionRepository athleteSubscriptionRepository,
            com.talentus.scout.repository.PaymentTransactionRepository paymentTransactionRepository,
            com.talentus.scout.service.storage.FileStorageService fileStorageService,
            AdminNotificationService adminNotificationService
    ) {
        this.athleteRepository = athleteRepository;
        this.userRepository = userRepository;
        this.clubService = clubService;
        this.slugService = slugService;
        this.athleteVideoRepository = athleteVideoRepository;
        this.athleteBadgeRepository = athleteBadgeRepository;
        this.athleteSubscriptionRepository = athleteSubscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.fileStorageService = fileStorageService;
        this.adminNotificationService = adminNotificationService;
    }

    @Transactional
    public AthleteResponse createAthlete(CreateAthleteRequest request, String tutorEmail) {
        User tutor = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Tutor no encontrado con email: " + tutorEmail));

        // Validación de edad: menores de 21 años
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        if (age > 21) {
            throw new IllegalArgumentException("El atleta debe pertenecer a categorías formativas menores a 21 años (Edad actual: " + age + ")");
        }

        ClubOrganization club = null;
        if (request.getCurrentClubId() != null) {
            club = clubService.getClubById(request.getCurrentClubId());
        } else if (request.getCustomClubName() != null && !request.getCustomClubName().trim().isEmpty()) {
            String resolvedCountry = (request.getCustomClubCountry() != null && !request.getCustomClubCountry().isBlank())
                    ? request.getCustomClubCountry().trim()
                    : ((tutor != null && tutor.getCountry() != null && !tutor.getCountry().isBlank()) ? tutor.getCountry() : "Venezuela");
            String resolvedState = (request.getCustomClubState() != null && !request.getCustomClubState().isBlank())
                    ? request.getCustomClubState().trim()
                    : ((tutor != null && tutor.getState() != null && !tutor.getState().isBlank()) ? tutor.getState() : "No especificado");
            String resolvedCity = (request.getCustomClubCity() != null && !request.getCustomClubCity().isBlank())
                    ? request.getCustomClubCity().trim() : "No especificada";

            club = clubService.getOrCreateCustomClub(
                    request.getCustomClubName(),
                    resolvedCity,
                    resolvedState,
                    resolvedCountry,
                    tutorEmail
            );
        }

        String slug = slugService.generateUniqueSlug(request.getFirstName(), request.getLastName());

        Athlete athlete = Athlete.builder()
                .tutor(tutor)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .birthDate(request.getBirthDate())
                .gender(request.getGender() != null ? request.getGender() : "MALE")
                .primaryPosition(request.getPrimaryPosition())
                .secondaryPosition(request.getSecondaryPosition())
                .preferredFoot(request.getPreferredFoot())
                .nationality(request.getNationality() != null ? request.getNationality() : "Venezolana")
                .secondaryPassports(request.getSecondaryPassports())
                .currentClub(club)
                .federationLicenseNumber(request.getFederationLicenseNumber())
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .profilePhotoUrl(request.getProfilePhotoUrl())
                .bio(request.getBio())
                .slug(slug)
                .isPublic(request.isPublic())
                .build();

        Athlete saved = athleteRepository.save(athlete);
        adminNotificationService.notifyNewAthleteRegistered(saved);

        return enrichAthleteResponse(new AthleteResponse(saved));
    }

    @Transactional(readOnly = true)
    public List<AthleteResponse> getMyAthletes(String tutorEmail) {
        User tutor = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Tutor no encontrado con email: " + tutorEmail));

        java.time.Instant now = java.time.Instant.now();
        return athleteRepository.findByTutorIdOrderByCreatedAtDesc(tutor.getId()).stream()
                .map(athlete -> {
                    AthleteResponse resp = enrichAthleteResponse(new AthleteResponse(athlete));
                    var activeSub = athleteSubscriptionRepository
                            .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), com.talentus.scout.domain.enums.SubscriptionStatus.ACTIVE);
                    if (activeSub.isPresent() && activeSub.get().getEndDate().isAfter(now)) {
                        resp.setSubscriptionStatus("ACTIVE");
                        resp.setSubscriptionEndDate(activeSub.get().getEndDate());
                        resp.setSubscriptionPlanName(activeSub.get().getPlan().getName());
                    } else {
                        var pendingTx = paymentTransactionRepository
                                .findFirstByAthleteIdAndStatusOrderByCreatedAtDesc(athlete.getId(), com.talentus.scout.domain.enums.PaymentStatus.PENDING_AUDIT);
                        if (pendingTx.isPresent()) {
                            resp.setSubscriptionStatus("PENDING_AUDIT");
                            resp.setSubscriptionPlanName(pendingTx.get().getPlan().getName());
                        } else {
                            resp.setSubscriptionStatus("INACTIVE");
                        }
                    }
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AthleteResponse getAthleteById(UUID id) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));
        return enrichAthleteResponse(new AthleteResponse(athlete));
    }

    @Transactional
    public AthleteResponse updateAthlete(UUID id, UpdateAthleteRequest request) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));

        if (request.getPrimaryPosition() != null && !request.getPrimaryPosition().isBlank()) {
            athlete.setPrimaryPosition(request.getPrimaryPosition());
        }
        if (request.getSecondaryPosition() != null) {
            athlete.setSecondaryPosition(request.getSecondaryPosition());
        }
        if (request.getPreferredFoot() != null && !request.getPreferredFoot().isBlank()) {
            athlete.setPreferredFoot(request.getPreferredFoot());
        }
        if (request.getCurrentClubId() != null) {
            ClubOrganization club = clubService.getClubById(request.getCurrentClubId());
            athlete.setCurrentClub(club);
        }
        if (request.getFederationLicenseNumber() != null) {
            athlete.setFederationLicenseNumber(request.getFederationLicenseNumber());
        }
        if (request.getSecondaryPassports() != null) {
            athlete.setSecondaryPassports(request.getSecondaryPassports());
        }
        if (request.getHeightCm() != null) {
            athlete.setHeightCm(request.getHeightCm());
        }
        if (request.getWeightKg() != null) {
            athlete.setWeightKg(request.getWeightKg());
        }
        if (request.getProfilePhotoUrl() != null) {
            athlete.setProfilePhotoUrl(request.getProfilePhotoUrl());
        }
        if (request.getBio() != null) {
            athlete.setBio(request.getBio());
        }
        if (request.getIsPublic() != null) {
            athlete.setPublic(request.getIsPublic());
        }

        Athlete updated = athleteRepository.save(athlete);
        return enrichAthleteResponse(new AthleteResponse(updated));
    }

    @Transactional
    public AthleteResponse uploadIdentityDocument(UUID id, com.talentus.scout.web.dto.UploadAthleteIdentityDocumentRequest request) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));

        if (request.getFile() == null || request.getFile().isEmpty()) {
            throw new IllegalArgumentException("El archivo del documento de identidad es obligatorio");
        }

        if (request.getFile().getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido (5MB)");
        }

        String originalFilename = request.getFile().getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (!lower.endsWith(".pdf") && !lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
                throw new IllegalArgumentException("Formato no permitido. Solo se aceptan archivos PDF, PNG o JPG.");
            }
        }

        String contentType = request.getFile().getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String lowerContentType = contentType.toLowerCase();
            if (!lowerContentType.equals("application/pdf") &&
                !lowerContentType.equals("image/png") &&
                !lowerContentType.equals("image/jpeg") &&
                !lowerContentType.equals("image/jpg")) {
                throw new IllegalArgumentException("Tipo de contenido no permitido. Solo se aceptan archivos PDF, PNG o JPG.");
            }
        }

        if (request.getDocumentNumber() == null || request.getDocumentNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("El número de documento es obligatorio");
        }

        String storedFileUrl = fileStorageService.storeFile(request.getFile(), "identity-documents/athletes");

        athlete.setIdentityDocumentType(request.getDocumentType());
        athlete.setIdentityDocumentNumber(request.getDocumentNumber().trim());
        athlete.setIdentityDocumentFileUrl(storedFileUrl);
        athlete.setLegalVerificationStatus(LegalVerificationStatus.PENDING_REVIEW);
        athlete.setLegalVerificationNotes(null);
        athlete.setLegalVerifiedBy(null);
        athlete.setLegalVerifiedAt(null);

        Athlete updated = athleteRepository.save(athlete);
        return enrichAthleteResponse(new AthleteResponse(updated));
    }

    @Transactional(readOnly = true)
    public PublicAthleteResponse getPublicAthleteBySlug(String slug) {
        Athlete athlete = athleteRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil público de atleta no encontrado: " + slug));

        if (!athlete.isPublic() || athlete.getLegalVerificationStatus() != LegalVerificationStatus.APPROVED) {
            throw new ResourceNotFoundException("El perfil del atleta no se encuentra público o requiere verificación de patria potestad.");
        }

        PublicClubDto clubDto = null;
        if (athlete.getCurrentClub() != null) {
            ClubOrganization club = athlete.getCurrentClub();
            clubDto = PublicClubDto.builder()
                    .name(club.getName())
                    .logoUrl(club.getLogoUrl())
                    .city(club.getCity())
                    .state(club.getState())
                    .country(club.getCountry())
                    .build();
        }

        List<AthleteBadge> badges = athleteBadgeRepository.findByAthleteIdAndStatus(athlete.getId(), BadgeStatus.APPROVED);
        boolean hasMedical = false;
        String medicalCertifier = null;
        List<PublicBadgeDto> badgeDtos = new ArrayList<>();
        for (AthleteBadge b : badges) {
            String orgName = b.getOrganization() != null ? b.getOrganization().getName() : null;
            if (b.getBadgeType() == BadgeType.MEDICAL_ANTHRO) {
                hasMedical = true;
                medicalCertifier = b.getVerificationNotes() != null && !b.getVerificationNotes().isBlank()
                        ? b.getVerificationNotes()
                        : "Médico Colegiado / Especialista";
            }
            badgeDtos.add(PublicBadgeDto.builder()
                    .type(b.getBadgeType())
                    .title(resolveBadgeTitle(b.getBadgeType(), orgName, b.getVerificationNotes()))
                    .verified(true)
                    .organizationName(orgName)
                    .verifiedAt(b.getVerifiedAt())
                    .build());
        }

        AthleteMetricsDto metricsDto = AthleteMetricsDto.builder()
                .heightCm(athlete.getHeightCm())
                .weightKg(athlete.getWeightKg())
                .medicalCertified(hasMedical)
                .medicalCertifierInfo(medicalCertifier)
                .build();

        // Si no posee badges aprobados en base de datos, estructurar badges automáticos basados en club y licencia federativa
        if (badgeDtos.isEmpty()) {
            if (athlete.getFederationLicenseNumber() != null && !athlete.getFederationLicenseNumber().isBlank()) {
                badgeDtos.add(PublicBadgeDto.builder()
                        .type(BadgeType.FEDERATION_LICENSE)
                        .title("Licencia Federada Oficial (" + athlete.getFederationLicenseNumber() + ")")
                        .verified(true)
                        .organizationName("Asociación / Federación Oficial")
                        .verifiedAt(athlete.getCreatedAt())
                        .build());
            }
            if (athlete.getCurrentClub() != null) {
                badgeDtos.add(PublicBadgeDto.builder()
                        .type(BadgeType.CLUB_AFFILIATION)
                        .title("Ficha Activa — " + athlete.getCurrentClub().getName())
                        .verified(true)
                        .organizationName(athlete.getCurrentClub().getName())
                        .verifiedAt(athlete.getCreatedAt())
                        .build());
            }
        }

        List<AthleteVideo> videos = athleteVideoRepository.findByAthleteIdOrderBySortOrderAscCreatedAtDesc(athlete.getId());
        List<PublicVideoDto> videoDtos = videos.stream()
                .filter(v -> v.getStatus() == VideoStatus.READY)
                .map(v -> PublicVideoDto.builder()
                        .id(v.getId())
                        .title(v.getTitle())
                        .category(v.getCategory())
                        .playbackUrl(v.getPlaybackUrl())
                        .thumbnailUrl(v.getThumbnailUrl())
                        .durationSeconds(v.getDurationSeconds())
                        .build())
                .collect(Collectors.toList());

        int age = athlete.getBirthDate() != null
                ? Period.between(athlete.getBirthDate(), LocalDate.now()).getYears()
                : 0;

        return PublicAthleteResponse.builder()
                .id(athlete.getId())
                .slug(athlete.getSlug())
                .firstName(athlete.getFirstName())
                .lastName(athlete.getLastName())
                .fullName(athlete.getFirstName() + " " + athlete.getLastName())
                .age(age)
                .birthDate(athlete.getBirthDate())
                .gender(athlete.getGender())
                .primaryPosition(athlete.getPrimaryPosition())
                .secondaryPosition(athlete.getSecondaryPosition())
                .preferredFoot(athlete.getPreferredFoot())
                .nationality(athlete.getNationality())
                .secondaryPassports(athlete.getSecondaryPassports())
                .profilePhotoUrl(athlete.getProfilePhotoUrl())
                .bio(athlete.getBio())
                .club(clubDto)
                .metrics(metricsDto)
                .badges(badgeDtos)
                .videos(videoDtos)
                .build();
    }

    private String resolveBadgeTitle(BadgeType type, String orgName, String verificationNotes) {
        if (type == null) return "Insignia Verificada";
        switch (type) {
            case FEDERATION_LICENSE:
                return "Licencia Federada Oficial" + (orgName != null ? " — " + orgName : "");
            case CLUB_AFFILIATION:
                return "Ficha Activa" + (orgName != null ? " — " + orgName : "");
            case MEDICAL_ANTHRO:
                return "Certificación Médica y Antropométrica";
            case SCOUT_ENDORSEMENT:
                return "Respaldo de Scout Acreditado";
            case SPORT_ACHIEVEMENT:
                return (verificationNotes != null && !verificationNotes.isBlank())
                        ? verificationNotes
                        : "Logro Deportivo / Palmarés Avalado";
            default:
                return "Verificación Oficial";
        }
    }

    @Transactional
    public AthleteResponse uploadProfilePhoto(UUID id, MultipartFile file) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de la fotografía es obligatorio");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido (5MB)");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".webp")) {
                throw new IllegalArgumentException("Formato no permitido. Solo se aceptan archivos PNG, JPG, JPEG o WEBP.");
            }
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String lowerContentType = contentType.toLowerCase();
            if (!lowerContentType.equals("image/png") &&
                !lowerContentType.equals("image/jpeg") &&
                !lowerContentType.equals("image/jpg") &&
                !lowerContentType.equals("image/webp")) {
                throw new IllegalArgumentException("Tipo de contenido no permitido. Solo se aceptan imágenes PNG, JPG o WEBP.");
            }
        }

        if (athlete.getProfilePhotoUrl() != null && !athlete.getProfilePhotoUrl().isBlank()) {
            fileStorageService.deleteFile(athlete.getProfilePhotoUrl());
        }

        String storedFileUrl = fileStorageService.storeFile(file, "photos");
        athlete.setProfilePhotoUrl(storedFileUrl);

        Athlete updated = athleteRepository.save(athlete);
        return new AthleteResponse(updated);
    }

    @Transactional
    public AthleteResponse deleteProfilePhoto(UUID id) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));

        if (athlete.getProfilePhotoUrl() != null && !athlete.getProfilePhotoUrl().isBlank()) {
            fileStorageService.deleteFile(athlete.getProfilePhotoUrl());
            athlete.setProfilePhotoUrl(null);
            Athlete updated = athleteRepository.save(athlete);
            return new AthleteResponse(updated);
        }

        return new AthleteResponse(athlete);
    }

    private AthleteResponse enrichAthleteResponse(AthleteResponse response) {
        if (response != null && response.getIdentityDocumentFileUrl() != null && !response.getIdentityDocumentFileUrl().isBlank()) {
            response.setIdentityDocumentFileUrl(fileStorageService.generatePresignedUrl(response.getIdentityDocumentFileUrl(), java.time.Duration.ofMinutes(5)));
        }
        return response;
    }
}

