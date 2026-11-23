package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.ScoutTechnicalAnalysis;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.ScoutTechnicalAnalysisRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.TechnicalAnalysisItemDto;
import com.talentus.scout.web.dto.UpsertScoutTechnicalAnalysisRequest;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ScoutTechnicalAnalysisControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private ScoutTechnicalAnalysisRepository analysisRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private Athlete athlete;
    private User scoutA;
    private User scoutB;
    private User adminUser;
    private String scoutAToken;
    private String scoutBToken;
    private String pendingScoutToken;
    private String tutorToken;
    private String adultAthleteToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User tutor = userRepository.save(User.builder()
                .email("tutor.ta." + suffix + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Tutor Legal TA")
                .phoneNumber("+584120001122")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());
        tutorToken = jwtService.generateToken(tutor);

        User adultAthleteUser = userRepository.save(User.builder()
                .email("athlete.ta." + suffix + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Atleta Mayor TA")
                .phoneNumber("+584120001133")
                .role(UserRole.ATHLETE)
                .status(AccountStatus.ACTIVE)
                .build());
        adultAthleteToken = jwtService.generateToken(adultAthleteUser);

        ClubOrganization club = clubOrganizationRepository.save(ClubOrganization.builder()
                .name("Deportivo Miranda " + suffix)
                .city("Caracas")
                .state("Miranda")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build());

        athlete = athleteRepository.save(Athlete.builder()
                .tutor(tutor)
                .firstName("Fabio")
                .lastName("Ciarpaglini")
                .birthDate(LocalDate.of(2015, 4, 10))
                .primaryPosition("MEDIOCAMPISTA_OFENSIVO")
                .preferredFoot("DIESTRO")
                .currentClub(club)
                .slug("fabio-ciarpaglini-" + suffix)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build());

        scoutA = createScout("scouta." + suffix + "@caracasfc.test", "Miguel Morena", "Jefe de Scouting", "Caracas FC", ScoutVerificationStatus.APPROVED, AccountStatus.ACTIVE);
        scoutAToken = jwtService.generateToken(scoutA);

        scoutB = createScout("scoutb." + suffix + "@vinotinto.test", "Carlos Mendoza", "Ojeador Nacional", "Selección Regional", ScoutVerificationStatus.APPROVED, AccountStatus.ACTIVE);
        scoutBToken = jwtService.generateToken(scoutB);

        User pendingScout = createScout("pending." + suffix + "@club.test", "Scout Pendiente", "Analista", "Club X", ScoutVerificationStatus.PENDING, AccountStatus.PENDING_VERIFICATION);
        pendingScoutToken = jwtService.generateToken(pendingScout);

        adminUser = userRepository.save(User.builder()
                .email("admin.ta." + suffix + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Admin Moderador")
                .phoneNumber("+584120009999")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
        adminToken = jwtService.generateToken(adminUser);
    }

    @Test
    void fullLifecycle_multipleScoutsCanAnalyzeAndReadButOnlyEditOwn() throws Exception {
        String baseUrl = "/api/v1/scout/athletes/" + athlete.getId() + "/technical-analyses";

        UpsertScoutTechnicalAnalysisRequest reqA = buildSampleRequest(
                "Élite técnica de su categoría",
                "Limpio en el primer control y conducción fluida.",
                "Autoconfianza en duelos",
                "Fortalecer convicción al disputar balones divididos.",
                "Prospecto de altísima riqueza técnica e inteligencia cognitiva."
        );

        // 1. Scout A (otro club distinto al atleta) crea análisis -> 201 Created
        String createdJsonA = mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + scoutAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scoutFullName").value("Miguel Morena"))
                .andExpect(jsonPath("$.scoutRole").value("Jefe de Scouting"))
                .andExpect(jsonPath("$.organizationName").value("Caracas FC"))
                .andExpect(jsonPath("$.strengths", hasSize(1)))
                .andExpect(jsonPath("$.improvementAreas", hasSize(1)))
                .andExpect(jsonPath("$.editableByCurrentUser").value(true))
                .andExpect(jsonPath("$.deletableByCurrentUser").value(true))
                .andReturn().getResponse().getContentAsString();

        String analysisAId = objectMapper.readTree(createdJsonA).get("id").asText();

        // 2. Scout A intenta crear segundo análisis sobre el mismo atleta -> 409 Conflict
        mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + scoutAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // 3. Scout B crea su propio análisis sobre el mismo atleta -> 201 Created
        UpsertScoutTechnicalAnalysisRequest reqB = buildSampleRequest(
                "Visión periférica",
                "Identifica líneas de pase interiores con rapidez.",
                "Golpeo largo",
                "Mejorar elevación de balón en cambios de orientación.",
                "Perfil asociativo muy interesante para seguimiento semestral."
        );

        mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + scoutBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated());

        // 4. Scout B lista análisis: ve ambos, primero el suyo (editable=true) y segundo el de Scout A (editable=false)
        mockMvc.perform(get(baseUrl)
                        .header("Authorization", "Bearer " + scoutBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].scoutFullName").value("Carlos Mendoza"))
                .andExpect(jsonPath("$[0].editableByCurrentUser").value(true))
                .andExpect(jsonPath("$[0].deletableByCurrentUser").value(true))
                .andExpect(jsonPath("$[1].scoutFullName").value("Miguel Morena"))
                .andExpect(jsonPath("$[1].editableByCurrentUser").value(false))
                .andExpect(jsonPath("$[1].deletableByCurrentUser").value(false));

        // 5. Scout B intenta editar análisis de Scout A -> 403 Forbidden
        mockMvc.perform(put(baseUrl + "/" + analysisAId)
                        .header("Authorization", "Bearer " + scoutBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isForbidden());

        // 6. Scout B intenta eliminar análisis de Scout A -> 403 Forbidden
        mockMvc.perform(delete(baseUrl + "/" + analysisAId)
                        .header("Authorization", "Bearer " + scoutBToken))
                .andExpect(status().isForbidden());

        // 7. Scout A edita su propio análisis -> 200 OK
        UpsertScoutTechnicalAnalysisRequest updatedReqA = buildSampleRequest(
                "Élite técnica confirmada",
                "Domina ambos perfiles en espacios reducidos.",
                "Consolidación física",
                "Ganar potencia en duelos divididos.",
                "Dictamen actualizado tras segunda visoría en torneo distrital."
        );

        mockMvc.perform(put(baseUrl + "/" + analysisAId)
                        .header("Authorization", "Bearer " + scoutAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedReqA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strengths[0].title").value("Élite técnica confirmada"))
                .andExpect(jsonPath("$.scoutingVerdict").value("Dictamen actualizado tras segunda visoría en torneo distrital."));

        // 8. Admin NO puede editar análisis ajeno (403), pero SÍ puede moderar/eliminar (204)
        mockMvc.perform(put(baseUrl + "/" + analysisAId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(baseUrl + "/" + analysisAId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertTrue(analysisRepository.findById(UUID.fromString(analysisAId)).isEmpty());
    }

    @Test
    void lopnnaIsolation_unauthorizedRolesAndPublicEndpointNeverAccessAnalyses() throws Exception {
        String baseUrl = "/api/v1/scout/athletes/" + athlete.getId() + "/technical-analyses";

        UpsertScoutTechnicalAnalysisRequest reqA = buildSampleRequest(
                "Control orientado",
                "Excelente recepción perfilada.",
                "Trabajo psicológico",
                "Reforzar confianza en contacto físico.",
                "Observación reservada exclusivamente para red de scouts."
        );

        mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + scoutAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated());

        // Anónimo, TUTOR, ATHLETE y SCOUT PENDING bloqueados
        mockMvc.perform(get(baseUrl))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));

        mockMvc.perform(get(baseUrl).header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(baseUrl).header("Authorization", "Bearer " + adultAthleteToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(baseUrl).header("Authorization", "Bearer " + pendingScoutToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isForbidden());

        // Endpoint público jamás incluye información de análisis técnicos
        String publicResponse = mockMvc.perform(get("/api/v1/athletes/public/" + athlete.getSlug()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(publicResponse.contains("Observación reservada exclusivamente"));
        assertFalse(publicResponse.contains("technicalAnalyses"));
        assertFalse(publicResponse.contains("scoutingVerdict"));
    }

    @Test
    void createAnalysis_withInvalidEmptyPayload_returns400BadRequest() throws Exception {
        String baseUrl = "/api/v1/scout/athletes/" + athlete.getId() + "/technical-analyses";

        UpsertScoutTechnicalAnalysisRequest invalid = UpsertScoutTechnicalAnalysisRequest.builder()
                .strengths(List.of())
                .improvementAreas(List.of())
                .scoutingVerdict("   ")
                .build();

        mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + scoutAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        UpsertScoutTechnicalAnalysisRequest invalidItem = buildSampleRequest(
                "   ",
                "Descripción válida",
                "Enfoque",
                "",
                "Dictamen válido"
        );

        mockMvc.perform(post(baseUrl)
                        .header("Authorization", "Bearer " + scoutAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidItem)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void deleteAnalysis_byOwnerScout_succeeds() throws Exception {
        String baseUrl = "/api/v1/scout/athletes/" + athlete.getId() + "/technical-analyses";

        ScoutTechnicalAnalysis saved = analysisRepository.save(ScoutTechnicalAnalysis.builder()
                .athlete(athlete)
                .scout(scoutA)
                .strengthsJson("[{\"title\":\"Velocidad\",\"description\":\"Arranque explosivo\"}]")
                .improvementAreasJson("[{\"title\":\"Definición\",\"description\":\"Calma en el área\"}]")
                .scoutingVerdict("Seguimiento positivo.")
                .build());

        mockMvc.perform(delete(baseUrl + "/" + saved.getId())
                        .header("Authorization", "Bearer " + scoutAToken))
                .andExpect(status().isNoContent());

        assertFalse(analysisRepository.existsByAthleteIdAndScoutId(athlete.getId(), scoutA.getId()));
    }

    private User createScout(
            String email,
            String fullName,
            String scoutRole,
            String orgName,
            ScoutVerificationStatus verificationStatus,
            AccountStatus accountStatus
    ) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName(fullName)
                .phoneNumber("+584121112233")
                .role(UserRole.SCOUT)
                .status(accountStatus)
                .build());

        scoutProfileRepository.save(ScoutProfile.builder()
                .user(user)
                .organizationName(orgName)
                .scoutRole(scoutRole)
                .credentialsDocumentUrl("/uploads/credentials/cred-" + UUID.randomUUID() + ".pdf")
                .verificationStatus(verificationStatus)
                .build());

        return user;
    }

    private UpsertScoutTechnicalAnalysisRequest buildSampleRequest(
            String strengthTitle,
            String strengthDesc,
            String improvementTitle,
            String improvementDesc,
            String verdict
    ) {
        return UpsertScoutTechnicalAnalysisRequest.builder()
                .strengths(List.of(TechnicalAnalysisItemDto.builder()
                        .title(strengthTitle)
                        .description(strengthDesc)
                        .build()))
                .improvementAreas(List.of(TechnicalAnalysisItemDto.builder()
                        .title(improvementTitle)
                        .description(improvementDesc)
                        .build()))
                .scoutingVerdict(verdict)
                .build();
    }
}
