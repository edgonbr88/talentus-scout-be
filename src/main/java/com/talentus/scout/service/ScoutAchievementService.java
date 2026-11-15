package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.ScoutSecurity;
import com.talentus.scout.web.dto.CreateAthleteAchievementRequest;
import com.talentus.scout.web.dto.publicprofile.PublicBadgeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoutAchievementService {

    private final AthleteRepository athleteRepository;
    private final AthleteBadgeRepository athleteBadgeRepository;
    private final UserRepository userRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final ScoutSecurity scoutSecurity;

    @Transactional
    public PublicBadgeDto registerClubAchievement(
            UUID athleteId,
            CreateAthleteAchievementRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado.");
        }

        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + athleteId));

        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));

        boolean isAdmin = scoutSecurity.hasRole(authentication, "ADMIN");
        if (!isAdmin) {
            if (!scoutSecurity.isVerifiedScout(authentication)) {
                throw new AccessDeniedException("Tu perfil de scout aún no está acreditado para certificar logros.");
            }
            validateSameClubMembership(athlete, currentUser, authentication);
        }

        String formattedNotes = buildVerificationNotes(request);
        Instant now = Instant.now();
        ClubOrganization club = athlete.getCurrentClub();

        AthleteBadge badge = AthleteBadge.builder()
                .athlete(athlete)
                .badgeType(BadgeType.SPORT_ACHIEVEMENT)
                .organization(club)
                .verifiedByUser(currentUser)
                .status(BadgeStatus.APPROVED)
                .verificationNotes(formattedNotes)
                .verifiedAt(now)
                .build();

        AthleteBadge saved = athleteBadgeRepository.save(badge);
        log.info("Logro deportivo certificado (badgeId={}) para atleta {} por usuario {}",
                saved.getId(), athleteId, currentUser.getEmail());

        String orgName = club != null ? club.getName() : null;
        return PublicBadgeDto.builder()
                .type(BadgeType.SPORT_ACHIEVEMENT)
                .title(formattedNotes)
                .verified(true)
                .organizationName(orgName)
                .verifiedAt(saved.getVerifiedAt())
                .build();
    }

    private void validateSameClubMembership(Athlete athlete, User currentUser, Authentication authentication) {
        ClubOrganization athleteClub = athlete.getCurrentClub();
        if (athleteClub == null || athleteClub.getName() == null || athleteClub.getName().isBlank()) {
            throw new AccessDeniedException("Solo puedes certificar logros de atletas que pertenezcan a tu mismo club u organización");
        }

        boolean matchesByClubId = scoutSecurity.resolveApprovedScoutClubId(authentication)
                .map(scoutClubId -> scoutClubId.equals(athleteClub.getId()))
                .orElse(false);

        if (matchesByClubId) {
            return;
        }

        ScoutProfile scoutProfile = scoutProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Solo puedes certificar logros de atletas que pertenezcan a tu mismo club u organización"));

        String scoutOrg = scoutProfile.getOrganizationName() != null ? scoutProfile.getOrganizationName().trim() : "";
        String athleteClubName = athleteClub.getName().trim();

        if (scoutOrg.isEmpty() || !scoutOrg.equalsIgnoreCase(athleteClubName)) {
            throw new AccessDeniedException("Solo puedes certificar logros de atletas que pertenezcan a tu mismo club u organización");
        }
    }

    private String buildVerificationNotes(CreateAthleteAchievementRequest request) {
        String title = request.getTitle().trim();
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            return title + " — " + request.getDescription().trim();
        }
        return title;
    }
}
