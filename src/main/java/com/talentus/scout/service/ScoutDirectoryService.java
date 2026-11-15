package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteVideoRepository;
import com.talentus.scout.security.ScoutSecurity;
import com.talentus.scout.specification.AthleteSpecifications;
import com.talentus.scout.web.dto.AthleteFilterCriteria;
import com.talentus.scout.web.dto.ScoutDirectoryAthleteItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoutDirectoryService {

    private final AthleteRepository athleteRepository;
    private final AthleteBadgeRepository athleteBadgeRepository;
    private final AthleteVideoRepository athleteVideoRepository;
    private final ScoutSecurity scoutSecurity;

    @Transactional(readOnly = true)
    public List<Integer> getAvailableBirthYears() {
        return getAvailableBirthYears(null);
    }

    @Transactional(readOnly = true)
    public List<Integer> getAvailableBirthYears(Authentication authentication) {
        return scoutSecurity.resolveApprovedScoutClubId(authentication)
                .map(athleteRepository::findDistinctBirthYearsForDirectoryWithClub)
                .orElseGet(athleteRepository::findDistinctBirthYearsWithActiveRadar);
    }

    @Transactional(readOnly = true)
    public Page<ScoutDirectoryAthleteItem> searchDirectory(AthleteFilterCriteria criteria, Pageable pageable) {
        return searchDirectory(criteria, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<ScoutDirectoryAthleteItem> searchDirectory(
            AthleteFilterCriteria criteria,
            Pageable pageable,
            Authentication authentication
    ) {
        log.debug("Ejecutando búsqueda en directorio de scouts con criterios: {}", criteria);

        Specification<Athlete> publicRadarSpec = Specification.where(AthleteSpecifications.isPublic())
                .and(AthleteSpecifications.notDeleted())
                .and(AthleteSpecifications.hasActiveSubscription())
                .and(AthleteSpecifications.hasLegalVerificationApproved());

        Optional<UUID> scoutClubIdOpt = scoutSecurity.resolveApprovedScoutClubId(authentication);
        Specification<Athlete> spec = scoutClubIdOpt
                .map(clubId -> Specification.where(publicRadarSpec)
                        .or(Specification.where(AthleteSpecifications.notDeleted())
                                .and(AthleteSpecifications.withClubId(clubId))))
                .orElse(publicRadarSpec);

        if (criteria != null) {
            if (criteria.getBirthYearFrom() != null || criteria.getBirthYearTo() != null) {
                spec = spec.and(AthleteSpecifications.withBirthYearRange(criteria.getBirthYearFrom(), criteria.getBirthYearTo()));
            } else if (criteria.getBirthYear() != null) {
                spec = spec.and(AthleteSpecifications.withBirthYear(criteria.getBirthYear()));
            }
            if (criteria.getPrimaryPosition() != null && !criteria.getPrimaryPosition().isBlank()) {
                spec = spec.and(AthleteSpecifications.withPrimaryPosition(criteria.getPrimaryPosition()));
            }
            if (criteria.getPreferredFoot() != null && !criteria.getPreferredFoot().isBlank()) {
                spec = spec.and(AthleteSpecifications.withPreferredFoot(criteria.getPreferredFoot()));
            }
            if (criteria.getClubId() != null) {
                spec = spec.and(AthleteSpecifications.withClubId(criteria.getClubId()));
            }
            if (criteria.getHasEuropeanPassport() != null) {
                spec = spec.and(AthleteSpecifications.withEuropeanPassport(criteria.getHasEuropeanPassport()));
            }
            if (criteria.getHasVideo() != null) {
                spec = spec.and(AthleteSpecifications.withVideo(criteria.getHasVideo()));
            }
            if (criteria.getResidenceCountry() != null && !criteria.getResidenceCountry().isBlank()) {
                spec = spec.and(AthleteSpecifications.withResidenceCountry(criteria.getResidenceCountry()));
            }
        }

        Page<Athlete> athletesPage = athleteRepository.findAll(spec, pageable);

        return athletesPage.map(this::mapToItem);
    }

    private ScoutDirectoryAthleteItem mapToItem(Athlete athlete) {
        int birthYear = athlete.getBirthDate() != null ? athlete.getBirthDate().getYear() : 0;
        String category = resolveCategory(birthYear);

        long verifiedBadgesCount = athleteBadgeRepository.countByAthleteIdAndStatus(athlete.getId(), BadgeStatus.APPROVED);

        List<AthleteVideo> readyVideos = athleteVideoRepository.findByAthleteIdAndStatusOrderBySortOrderAscCreatedAtDesc(
                athlete.getId(),
                VideoStatus.READY
        );

        boolean hasVideo = !readyVideos.isEmpty();
        String featuredPlaybackUrl = null;
        String featuredThumbnailUrl = null;
        String featuredTitle = null;

        if (hasVideo) {
            AthleteVideo primaryVideo = readyVideos.get(0);
            featuredPlaybackUrl = primaryVideo.getPlaybackUrl();
            featuredThumbnailUrl = primaryVideo.getThumbnailUrl();
            featuredTitle = primaryVideo.getTitle();
        }

        boolean hasEuropeanPassport = athlete.getSecondaryPassports() != null &&
                !athlete.getSecondaryPassports().trim().isEmpty();

        boolean medicalCertified = athleteBadgeRepository.existsByAthleteIdAndBadgeTypeAndStatus(
                athlete.getId(),
                com.talentus.scout.domain.enums.BadgeType.MEDICAL_ANTHRO,
                BadgeStatus.APPROVED
        );

        String residenceCountry = athlete.getTutor() != null ? athlete.getTutor().getCountry() : null;

        return ScoutDirectoryAthleteItem.builder()
                .id(athlete.getId())
                .firstName(athlete.getFirstName())
                .lastName(athlete.getLastName())
                .birthDate(athlete.getBirthDate())
                .birthYear(birthYear)
                .category(category)
                .primaryPosition(athlete.getPrimaryPosition())
                .secondaryPosition(athlete.getSecondaryPosition())
                .preferredFoot(athlete.getPreferredFoot())
                .nationality(athlete.getNationality())
                .secondaryPassports(athlete.getSecondaryPassports())
                .hasEuropeanPassport(hasEuropeanPassport)
                .clubId(athlete.getCurrentClub() != null ? athlete.getCurrentClub().getId() : null)
                .clubName(athlete.getCurrentClub() != null ? athlete.getCurrentClub().getName() : null)
                .clubLogoUrl(athlete.getCurrentClub() != null ? athlete.getCurrentClub().getLogoUrl() : null)
                .profilePhotoUrl(athlete.getProfilePhotoUrl())
                .slug(athlete.getSlug())
                .verifiedBadgesCount(verifiedBadgesCount)
                .hasVideo(hasVideo)
                .featuredVideoPlaybackUrl(featuredPlaybackUrl)
                .featuredVideoThumbnailUrl(featuredThumbnailUrl)
                .featuredVideoTitle(featuredTitle)
                .medicalCertifiedMetrics(medicalCertified)
                .residenceCountry(residenceCountry)
                .updatedAt(athlete.getUpdatedAt())
                .build();
    }

    private String resolveCategory(int birthYear) {
        if (birthYear >= 2011) {
            return "Sub-15";
        } else if (birthYear >= 2009) {
            return "Sub-17";
        } else if (birthYear >= 2006) {
            return "Sub-20";
        } else {
            return "Senior";
        }
    }
}
