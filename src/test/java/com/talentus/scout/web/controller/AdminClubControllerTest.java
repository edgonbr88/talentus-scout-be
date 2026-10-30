package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.admin.club.CreateClubRequest;
import com.talentus.scout.web.dto.admin.club.ToggleVerificationRequest;
import com.talentus.scout.web.dto.admin.club.UpdateClubRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminClubControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String adminToken;
    private String tutorToken;
    private User tutorUser;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User admin = userRepository.findByEmail("admin.clubs@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Admin Clubes")
                        .email("admin.clubs@talentus.app")
                        .passwordHash(passwordEncoder.encode("AdminPass123*"))
                        .role(UserRole.ADMIN)
                        .phoneNumber("+584149999001")
                        .build()));
        adminToken = jwtService.generateToken(admin);

        tutorUser = userRepository.findByEmail("tutor.clubs@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Tutor Clubes")
                        .email("tutor.clubs@talentus.app")
                        .passwordHash(passwordEncoder.encode("TutorPass123*"))
                        .role(UserRole.TUTOR)
                        .phoneNumber("+584149999002")
                        .build()));
        tutorToken = jwtService.generateToken(tutorUser);
    }

    @Test
    @DisplayName("Petición sin autenticación es rechazada")
    void unauthorizedAccess_returns401Or403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clubs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario con rol TUTOR no puede acceder al panel administrativo (403 Forbidden)")
    void tutorAccess_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clubs")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede listar clubes paginados y obtener estadísticas")
    void getClubsAndStats_asAdmin_success() throws Exception {
        ClubOrganization club = clubOrganizationRepository.save(ClubOrganization.builder()
                .name("Club Test Alfa " + UUID.randomUUID().toString().substring(0, 6))
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build());

        mockMvc.perform(get("/api/v1/admin/clubs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "VERIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].id", hasItem(club.getId().toString())));

        mockMvc.perform(get("/api/v1/admin/clubs/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClubs", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.verifiedClubs", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Admin puede crear y actualizar un club oficial")
    void createAndUpdateClub_asAdmin_success() throws Exception {
        String clubName = "Academia Titanes " + UUID.randomUUID().toString().substring(0, 5);

        CreateClubRequest createRequest = CreateClubRequest.builder()
                .name(clubName)
                .federationCode("FVF-7788")
                .city("Valencia")
                .state("Carabobo")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build();

        String responseBody = mockMvc.perform(post("/api/v1/admin/clubs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is(clubName)))
                .andExpect(jsonPath("$.verifiedOfficial", is(true)))
                .andReturn().getResponse().getContentAsString();

        UUID clubId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

        // Actualizar datos del club
        UpdateClubRequest updateRequest = UpdateClubRequest.builder()
                .name(clubName + " Actualizado")
                .federationCode("FVF-7788-B")
                .city("Naguanagua")
                .state("Carabobo")
                .country("Venezuela")
                .verifiedOfficial(false)
                .build();

        mockMvc.perform(put("/api/v1/admin/clubs/" + clubId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(clubName + " Actualizado")))
                .andExpect(jsonPath("$.city", is("Naguanagua")))
                .andExpect(jsonPath("$.verifiedOfficial", is(false)));

        // Toggle de verificación
        mockMvc.perform(patch("/api/v1/admin/clubs/" + clubId + "/verify")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ToggleVerificationRequest.builder().verifiedOfficial(true).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedOfficial", is(true)));
    }

    @Test
    @DisplayName("Eliminar club desvincula atletas de forma segura y revoca scouts a subsanación")
    void deleteClub_integrityPolicy_success() throws Exception {
        String clubName = "Club Deportivo Baruta " + UUID.randomUUID().toString().substring(0, 5);
        ClubOrganization club = clubOrganizationRepository.save(ClubOrganization.builder()
                .name(clubName)
                .city("Baruta")
                .state("Miranda")
                .country("Venezuela")
                .verifiedOfficial(false)
                .build());

        // 1. Crear atleta vinculado
        Athlete athlete = athleteRepository.save(Athlete.builder()
                .tutor(tutorUser)
                .firstName("Luis")
                .lastName("Gómez")
                .birthDate(LocalDate.of(2010, 5, 12))
                .primaryPosition("Delantero")
                .preferredFoot("Derecho")
                .slug("luis-gomez-" + UUID.randomUUID().toString().substring(0, 6))
                .currentClub(club)
                .build());

        // 2. Crear scout vinculado a esta organización
        User scoutUser = userRepository.save(User.builder()
                .fullName("Scout Baruta")
                .email("scout.baruta." + UUID.randomUUID().toString().substring(0, 5) + "@talentus.app")
                .passwordHash(passwordEncoder.encode("ScoutPass123*"))
                .role(UserRole.SCOUT)
                .phoneNumber("+584149999003")
                .build());

        ScoutProfile scoutProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(scoutUser)
                .organizationName(clubName)
                .scoutRole("Director Técnico")
                .credentialsDocumentUrl("/uploads/docs/cred.pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .build());

        // 3. Ejecutar DELETE /api/v1/admin/clubs/{id}
        mockMvc.perform(delete("/api/v1/admin/clubs/" + club.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // 4. Verificar integridad:
        // El club ya no existe
        Optional<ClubOrganization> deletedClub = clubOrganizationRepository.findById(club.getId());
        assertThat(deletedClub).isEmpty();

        // El atleta sigue existiendo pero su currentClub es null
        Athlete reloadedAthlete = athleteRepository.findById(athlete.getId()).orElseThrow();
        assertThat(reloadedAthlete.getCurrentClub()).isNull();

        // El scout pasa a REJECTED con motivo de subsanación
        ScoutProfile reloadedScout = scoutProfileRepository.findById(scoutProfile.getId()).orElseThrow();
        assertThat(reloadedScout.getVerificationStatus()).isEqualTo(ScoutVerificationStatus.REJECTED);
        assertThat(reloadedScout.getRejectionReason()).contains("dada de baja");
    }
}
