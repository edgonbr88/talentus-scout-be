package com.talentus.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.ScoutTechnicalAnalysis;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.DuplicateTechnicalAnalysisException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.ScoutTechnicalAnalysisRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.ScoutSecurity;
import com.talentus.scout.web.dto.ScoutTechnicalAnalysisResponse;
import com.talentus.scout.web.dto.TechnicalAnalysisItemDto;
import com.talentus.scout.web.dto.UpsertScoutTechnicalAnalysisRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoutTechnicalAnalysisServiceTest {

    @Mock
    private ScoutTechnicalAnalysisRepository analysisRepository;

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScoutProfileRepository scoutProfileRepository;

    @Mock
    private ScoutSecurity scoutSecurity;

    private ScoutTechnicalAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ScoutTechnicalAnalysisService(
                analysisRepository,
                athleteRepository,
                userRepository,
                scoutProfileRepository,
                scoutSecurity,
                new ObjectMapper()
        );
    }

    @Test
    void createAnalysis_validApprovedScout_savesAndReturnsResponse() {
        UUID athleteId = UUID.randomUUID();
        UUID scoutId = UUID.randomUUID();
        Authentication auth = scoutAuth("scout@miranda.com");

        Athlete athlete = publicApprovedAthlete(athleteId);
        User scoutUser = User.builder().id(scoutId).email("scout@miranda.com").fullName("Miguel Morena").role(UserRole.SCOUT).build();
        ScoutProfile profile = ScoutProfile.builder().user(scoutUser).scoutRole("Jefe de Scouting").organizationName("Deportivo Miranda").build();

        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);
        when(userRepository.findByEmail("scout@miranda.com")).thenReturn(Optional.of(scoutUser));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(analysisRepository.existsByAthleteIdAndScoutId(athleteId, scoutId)).thenReturn(false);
        when(analysisRepository.save(any(ScoutTechnicalAnalysis.class))).thenAnswer(inv -> {
            ScoutTechnicalAnalysis saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(scoutProfileRepository.findByUserId(scoutId)).thenReturn(Optional.of(profile));

        UpsertScoutTechnicalAnalysisRequest request = UpsertScoutTechnicalAnalysisRequest.builder()
                .strengths(List.of(TechnicalAnalysisItemDto.builder()
                        .title("Élite técnica")
                        .description("Primer control orientado limpio bajo presión.")
                        .build()))
                .improvementAreas(List.of(TechnicalAnalysisItemDto.builder()
                        .title("Seguridad en duelos")
                        .description("Afianzar convicción al disputar balones divididos.")
                        .build()))
                .scoutingVerdict("Prospecto con alta inteligencia táctica y margen de evolución.")
                .build();

        ScoutTechnicalAnalysisResponse response = service.createAnalysis(athleteId, request, auth);

        assertNotNull(response.getId());
        assertEquals("Miguel Morena", response.getScoutFullName());
        assertEquals("Jefe de Scouting", response.getScoutRole());
        assertEquals("Deportivo Miranda", response.getOrganizationName());
        assertEquals(1, response.getStrengths().size());
        assertEquals("Élite técnica", response.getStrengths().get(0).getTitle());
        assertEquals(1, response.getImprovementAreas().size());
        assertTrue(response.isEditableByCurrentUser());
        assertTrue(response.isDeletableByCurrentUser());
    }

    @Test
    void createAnalysis_duplicateForSameScoutAndAthlete_throwsConflict() {
        UUID athleteId = UUID.randomUUID();
        UUID scoutId = UUID.randomUUID();
        Authentication auth = scoutAuth("scout@miranda.com");

        Athlete athlete = publicApprovedAthlete(athleteId);
        User scoutUser = User.builder().id(scoutId).email("scout@miranda.com").fullName("Miguel Morena").role(UserRole.SCOUT).build();

        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);
        when(userRepository.findByEmail("scout@miranda.com")).thenReturn(Optional.of(scoutUser));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(analysisRepository.existsByAthleteIdAndScoutId(athleteId, scoutId)).thenReturn(true);

        UpsertScoutTechnicalAnalysisRequest request = UpsertScoutTechnicalAnalysisRequest.builder()
                .strengths(List.of(TechnicalAnalysisItemDto.builder().title("A").description("B").build()))
                .improvementAreas(List.of(TechnicalAnalysisItemDto.builder().title("C").description("D").build()))
                .scoutingVerdict("Dictamen")
                .build();

        assertThrows(DuplicateTechnicalAnalysisException.class, () -> service.createAnalysis(athleteId, request, auth));
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void listByAthlete_prioritizesCurrentUserAnalysisFirst() {
        UUID athleteId = UUID.randomUUID();
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Authentication auth = scoutAuth("yo@club.com");

        User me = User.builder().id(myId).email("yo@club.com").fullName("Mi Scout").build();
        User other = User.builder().id(otherId).email("otro@club.com").fullName("Otro Scout").build();
        Athlete athlete = publicApprovedAthlete(athleteId);

        ScoutTechnicalAnalysis otherAnalysis = ScoutTechnicalAnalysis.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .scout(other)
                .strengthsJson("[{\"title\":\"F1\",\"description\":\"D1\"}]")
                .improvementAreasJson("[{\"title\":\"M1\",\"description\":\"DM1\"}]")
                .scoutingVerdict("Otro dictamen")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ScoutTechnicalAnalysis myAnalysis = ScoutTechnicalAnalysis.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .scout(me)
                .strengthsJson("[{\"title\":\"F2\",\"description\":\"D2\"}]")
                .improvementAreasJson("[{\"title\":\"M2\",\"description\":\"DM2\"}]")
                .scoutingVerdict("Mi dictamen")
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now().minusSeconds(60))
                .build();

        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);
        when(userRepository.findByEmail("yo@club.com")).thenReturn(Optional.of(me));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(analysisRepository.findByAthleteIdWithScoutOrderByUpdatedAtDesc(athleteId))
                .thenReturn(List.of(otherAnalysis, myAnalysis));
        when(scoutProfileRepository.findByUserIdIn(Set.of(myId, otherId))).thenReturn(List.of(
                ScoutProfile.builder().user(me).scoutRole("Ojeador").organizationName("Club A").build(),
                ScoutProfile.builder().user(other).scoutRole("Director").organizationName("Club B").build()
        ));

        List<ScoutTechnicalAnalysisResponse> result = service.listByAthlete(athleteId, auth);

        assertEquals(2, result.size());
        assertEquals(myId, result.get(0).getScoutUserId());
        assertTrue(result.get(0).isEditableByCurrentUser());
        assertEquals(otherId, result.get(1).getScoutUserId());
        assertFalse(result.get(1).isEditableByCurrentUser());
        assertFalse(result.get(1).isDeletableByCurrentUser());
    }

    @Test
    void updateAnalysis_byAnotherScout_throwsAccessDenied() {
        UUID athleteId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Authentication auth = scoutAuth("intruder@club.com");

        User owner = User.builder().id(ownerId).email("owner@club.com").fullName("Autor").build();
        User intruder = User.builder().id(otherId).email("intruder@club.com").fullName("Otro").build();
        Athlete athlete = publicApprovedAthlete(athleteId);

        ScoutTechnicalAnalysis existing = ScoutTechnicalAnalysis.builder()
                .id(analysisId)
                .athlete(athlete)
                .scout(owner)
                .strengthsJson("[]")
                .improvementAreasJson("[]")
                .scoutingVerdict("Original")
                .build();

        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(false);
        when(scoutSecurity.isVerifiedScout(auth)).thenReturn(true);
        when(userRepository.findByEmail("intruder@club.com")).thenReturn(Optional.of(intruder));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(analysisRepository.findByIdAndAthleteIdWithScout(analysisId, athleteId)).thenReturn(Optional.of(existing));

        UpsertScoutTechnicalAnalysisRequest request = UpsertScoutTechnicalAnalysisRequest.builder()
                .strengths(List.of(TechnicalAnalysisItemDto.builder().title("X").description("Y").build()))
                .improvementAreas(List.of(TechnicalAnalysisItemDto.builder().title("Z").description("W").build()))
                .scoutingVerdict("Editado")
                .build();

        assertThrows(AccessDeniedException.class, () -> service.updateAnalysis(athleteId, analysisId, request, auth));
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void deleteAnalysis_byAdminOnOtherScoutAnalysis_succeeds() {
        UUID athleteId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@talentus.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        User owner = User.builder().id(ownerId).email("owner@club.com").build();
        User admin = User.builder().id(adminId).email("admin@talentus.com").role(UserRole.ADMIN).build();
        Athlete athlete = publicApprovedAthlete(athleteId);

        ScoutTechnicalAnalysis existing = ScoutTechnicalAnalysis.builder()
                .id(analysisId)
                .athlete(athlete)
                .scout(owner)
                .build();

        when(scoutSecurity.hasRole(auth, "ADMIN")).thenReturn(true);
        when(userRepository.findByEmail("admin@talentus.com")).thenReturn(Optional.of(admin));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(analysisRepository.findByIdAndAthleteIdWithScout(analysisId, athleteId)).thenReturn(Optional.of(existing));

        service.deleteAnalysis(athleteId, analysisId, auth);

        verify(analysisRepository).delete(existing);
    }

    private Authentication scoutAuth(String email) {
        return new UsernamePasswordAuthenticationToken(
                email,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_SCOUT"))
        );
    }

    private Athlete publicApprovedAthlete(UUID athleteId) {
        return Athlete.builder()
                .id(athleteId)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build();
    }
}
