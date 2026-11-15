package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.ScoutSecurity;
import com.talentus.scout.web.dto.CreateAthleteAchievementRequest;
import com.talentus.scout.web.dto.publicprofile.PublicBadgeDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoutAchievementServiceTest {

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private AthleteBadgeRepository athleteBadgeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScoutProfileRepository scoutProfileRepository;

    @Mock
    private ScoutSecurity scoutSecurity;

    @InjectMocks
    private ScoutAchievementService scoutAchievementService;

    @Test
    void registerClubAchievement_sameClub_succeedsAndSavesApprovedBadge() {
        UUID athleteId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID scoutUserId = UUID.randomUUID();

        ClubOrganization club = ClubOrganization.builder()
                .id(clubId)
                .name("Caracas FC")
                .build();

        Athlete athlete = Athlete.builder()
                .id(athleteId)
                .currentClub(club)
                .build();

        User scoutUser = User.builder()
                .id(scoutUserId)
                .email("scout@caracasfc.com")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "scout@caracasfc.com",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_SCOUT"))
        );

        CreateAthleteAchievementRequest request = CreateAthleteAchievementRequest.builder()
                .title("Campeón Torneo Apertura Sub-16")
                .category("TORNEO")
                .achievementDate(LocalDate.of(2026, 6, 15))
                .description("Titular indiscutible en la final")
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail("scout@caracasfc.com")).thenReturn(Optional.of(scoutUser));
        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);
        when(scoutSecurity.resolveApprovedScoutClubId(auth)).thenReturn(Optional.of(clubId));
        when(athleteBadgeRepository.save(any(AthleteBadge.class))).thenAnswer(inv -> {
            AthleteBadge b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        PublicBadgeDto result = scoutAchievementService.registerClubAchievement(athleteId, request, auth);

        assertNotNull(result);
        assertEquals(BadgeType.SPORT_ACHIEVEMENT, result.getType());
        assertEquals("Campeón Torneo Apertura Sub-16 — Titular indiscutible en la final", result.getTitle());
        assertEquals("Caracas FC", result.getOrganizationName());
        assertTrue(result.isVerified());
        assertNotNull(result.getVerifiedAt());

        ArgumentCaptor<AthleteBadge> captor = ArgumentCaptor.forClass(AthleteBadge.class);
        verify(athleteBadgeRepository).save(captor.capture());
        AthleteBadge saved = captor.getValue();
        assertEquals(BadgeStatus.APPROVED, saved.getStatus());
        assertEquals(BadgeType.SPORT_ACHIEVEMENT, saved.getBadgeType());
        assertEquals(club, saved.getOrganization());
        assertEquals(scoutUser, saved.getVerifiedByUser());
    }

    @Test
    void registerClubAchievement_differentClub_throwsAccessDenied() {
        UUID athleteId = UUID.randomUUID();
        UUID athleteClubId = UUID.randomUUID();
        UUID scoutClubId = UUID.randomUUID();
        UUID scoutUserId = UUID.randomUUID();

        ClubOrganization athleteClub = ClubOrganization.builder()
                .id(athleteClubId)
                .name("Deportivo Táchira")
                .build();

        Athlete athlete = Athlete.builder()
                .id(athleteId)
                .currentClub(athleteClub)
                .build();

        User scoutUser = User.builder()
                .id(scoutUserId)
                .email("scout@caracasfc.com")
                .build();

        ScoutProfile scoutProfile = ScoutProfile.builder()
                .user(scoutUser)
                .organizationName("Caracas FC")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "scout@caracasfc.com",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_SCOUT"))
        );

        CreateAthleteAchievementRequest request = CreateAthleteAchievementRequest.builder()
                .title("MVP Copa Regional")
                .category("INDIVIDUAL")
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail("scout@caracasfc.com")).thenReturn(Optional.of(scoutUser));
        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);
        when(scoutSecurity.resolveApprovedScoutClubId(auth)).thenReturn(Optional.of(scoutClubId));
        when(scoutProfileRepository.findByUserId(scoutUserId)).thenReturn(Optional.of(scoutProfile));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> scoutAchievementService.registerClubAchievement(athleteId, request, auth)
        );

        assertEquals("Solo puedes certificar logros de atletas que pertenezcan a tu mismo club u organización", ex.getMessage());
        verify(athleteBadgeRepository, never()).save(any());
    }

    @Test
    void registerClubAchievement_athleteWithoutClub_throwsAccessDenied() {
        UUID athleteId = UUID.randomUUID();
        UUID scoutUserId = UUID.randomUUID();

        Athlete athlete = Athlete.builder()
                .id(athleteId)
                .currentClub(null)
                .build();

        User scoutUser = User.builder()
                .id(scoutUserId)
                .email("scout@caracasfc.com")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "scout@caracasfc.com",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_SCOUT"))
        );

        CreateAthleteAchievementRequest request = CreateAthleteAchievementRequest.builder()
                .title("Convocatoria Selección Estadal")
                .category("CONVOCATORIA")
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail("scout@caracasfc.com")).thenReturn(Optional.of(scoutUser));
        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);

        assertThrows(
                AccessDeniedException.class,
                () -> scoutAchievementService.registerClubAchievement(athleteId, request, auth)
        );
        verify(athleteBadgeRepository, never()).save(any());
    }
}
