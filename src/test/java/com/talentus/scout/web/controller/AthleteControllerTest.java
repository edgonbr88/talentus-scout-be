package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AthleteControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private String tutorToken;
    private String otherTutorToken;
    private User tutorUser;
    private User otherTutorUser;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String email1 = "tutor.primary." + System.currentTimeMillis() + "@talentus.app";
        tutorUser = new User(email1, passwordEncoder.encode("Password123*"), "Tutor Principal", "+584141112233", UserRole.TUTOR, AccountStatus.ACTIVE);
        tutorUser = userRepository.save(tutorUser);
        tutorToken = jwtService.generateToken(tutorUser);

        String email2 = "tutor.secondary." + System.currentTimeMillis() + "@talentus.app";
        otherTutorUser = new User(email2, passwordEncoder.encode("Password123*"), "Tutor Secundario", "+584149998877", UserRole.TUTOR, AccountStatus.ACTIVE);
        otherTutorUser = userRepository.save(otherTutorUser);
        otherTutorToken = jwtService.generateToken(otherTutorUser);
    }

    @Test
    void getClubs_PublicAccess_ReturnsClubsList() throws Exception {
        mockMvc.perform(get("/api/v1/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createAthlete_WithValidData_Returns201AndGeneratedSlug() throws Exception {
        long suffix = System.currentTimeMillis();
        String payload = """
                {
                    "firstName": "Gabriel",
                    "lastName": "Perez-%d",
                    "birthDate": "2010-06-15",
                    "primaryPosition": "DELANTERO_CENTRO",
                    "preferredFoot": "DERECHO",
                    "heightCm": 174.50,
                    "weightKg": 63.00
                }
                """.formatted(suffix);

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Gabriel Perez-" + suffix))
                .andExpect(jsonPath("$.slug").value("gabriel-perez-" + suffix))
                .andExpect(jsonPath("$.age").value(16))
                .andExpect(jsonPath("$.primaryPosition").value("DELANTERO_CENTRO"))
                .andExpect(jsonPath("$.heightCm").value(174.50));
    }

    @Test
    void createAthlete_WithAgeOver21_Returns400BadRequest() throws Exception {
        String payload = """
                {
                    "firstName": "Adulto",
                    "lastName": "Jugador",
                    "birthDate": "1995-01-01",
                    "primaryPosition": "MEDIOCAMPISTA",
                    "preferredFoot": "DERECHO"
                }
                """;

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void athleteOwnership_IsolationBetweenTutors() throws Exception {
        // 1. Tutor 1 crea un atleta
        String createPayload = """
                {
                    "firstName": "Santiago",
                    "lastName": "Silva",
                    "birthDate": "2011-03-20",
                    "primaryPosition": "DEFENSA_CENTRAL",
                    "preferredFoot": "DERECHO"
                }
                """;

        String createResp = mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String athleteIdStr = com.jayway.jsonpath.JsonPath.read(createResp, "$.id");
        UUID athleteId = UUID.fromString(athleteIdStr);
        assertNotNull(athleteId);

        // 2. Tutor 1 puede ver a su atleta
        mockMvc.perform(get("/api/v1/athletes/" + athleteId)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Santiago Silva"));

        // 3. Tutor 2 intenta ver al atleta de Tutor 1 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/athletes/" + athleteId)
                        .header("Authorization", "Bearer " + otherTutorToken))
                .andExpect(status().isForbidden());

        // 4. Tutor 2 intenta actualizar al atleta de Tutor 1 -> 403 Forbidden
        String updatePayload = """
                {
                    "primaryPosition": "PORTERO"
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athleteId)
                        .header("Authorization", "Bearer " + otherTutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());

        // 5. Tutor 1 actualiza exitosamente a su atleta -> 200 OK
        mockMvc.perform(put("/api/v1/athletes/" + athleteId)
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryPosition").value("PORTERO"));
    }

    @Test
    void getMyAthletes_ReturnsOnlyAthletesOfAuthenticatedTutor() throws Exception {
        String payload = """
                {
                    "firstName": "Mateo",
                    "lastName": "Morales",
                    "birthDate": "2012-08-10",
                    "primaryPosition": "EXTREMO_IZQUIERDO",
                    "preferredFoot": "IZQUIERDO"
                }
                """;

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Consultar lista de mis atletas
        mockMvc.perform(get("/api/v1/athletes/my-athletes")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].firstName").value("Mateo"));

        // Otro tutor no debe ver a Mateo
        mockMvc.perform(get("/api/v1/athletes/my-athletes")
                        .header("Authorization", "Bearer " + otherTutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void downloadCvPdf_WithoutToken_ReturnsUnauthorizedOrForbidden() throws Exception {
        UUID dummyId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/athletes/" + dummyId + "/cv-pdf"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void downloadCvPdf_AsOwnerTutor_Returns200AndPdfContentType() throws Exception {
        // 1. Crear atleta perteneciente a tutorToken
        String payload = """
                {
                    "firstName": "Lucas",
                    "lastName": "Rivas",
                    "birthDate": "2011-04-20",
                    "primaryPosition": "MEDIOCAMPISTA",
                    "preferredFoot": "DERECHO"
                }
                """;

        String createResp = mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String athleteIdStr = com.jayway.jsonpath.JsonPath.read(createResp, "$.id");

        // 2. Descargar CV como tutor propietario
        byte[] pdfResponse = mockMvc.perform(get("/api/v1/athletes/" + athleteIdStr + "/cv-pdf")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline; filename=")))
                .andReturn().getResponse().getContentAsByteArray();

        org.junit.jupiter.api.Assertions.assertNotNull(pdfResponse);
        org.junit.jupiter.api.Assertions.assertTrue(pdfResponse.length > 500);

        // 3. Otro tutor no autorizado debe recibir 403 Forbidden
        mockMvc.perform(get("/api/v1/athletes/" + athleteIdStr + "/cv-pdf")
                        .header("Authorization", "Bearer " + otherTutorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAthlete_WithAdminRole_Returns403Forbidden() throws Exception {
        String adminEmail = "admin.athlete." + System.currentTimeMillis() + "@talentus.app";
        User adminUser = new User(adminEmail, passwordEncoder.encode("Password123*"), "Admin Auditor", "+584140003344", UserRole.ADMIN, AccountStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);
        String adminToken = jwtService.generateToken(adminUser);

        String payload = """
                {
                    "firstName": "Atleta",
                    "lastName": "Prohibido",
                    "birthDate": "2010-06-15",
                    "primaryPosition": "DELANTERO_CENTRO",
                    "preferredFoot": "DERECHO"
                }
                """;

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyAthletes_WithAdminRole_Returns403Forbidden() throws Exception {
        String adminEmail = "admin.myathletes." + System.currentTimeMillis() + "@talentus.app";
        User adminUser = new User(adminEmail, passwordEncoder.encode("Password123*"), "Admin Auditor", "+584140003355", UserRole.ADMIN, AccountStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);
        String adminToken = jwtService.generateToken(adminUser);

        mockMvc.perform(get("/api/v1/athletes/my-athletes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAthlete_WithUnauthorizedNationality_Returns400BadRequest() throws Exception {
        String payload = """
                {
                    "firstName": "Lucas",
                    "lastName": "Modric",
                    "birthDate": "2010-06-15",
                    "primaryPosition": "MEDIOCAMPISTA",
                    "preferredFoot": "DERECHO",
                    "nationality": "Croacia"
                }
                """;

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nationality").exists());
    }

    @Test
    void createAthlete_WithCustomClub_CreatesUnverifiedClubOrganization() throws Exception {
        String uniqueClubName = "Escuela Comunitaria " + System.currentTimeMillis();
        String payload = """
                {
                    "firstName": "Mateo",
                    "lastName": "Rios",
                    "birthDate": "2012-08-10",
                    "primaryPosition": "EXTREMO_DERECHO",
                    "preferredFoot": "DERECHO",
                    "nationality": "Venezuela",
                    "customClubName": "%s"
                }
                """.formatted(uniqueClubName);

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentClub").exists())
                .andExpect(jsonPath("$.currentClub.name").value(uniqueClubName))
                .andExpect(jsonPath("$.currentClub.verifiedOfficial").value(false));
    }

    @Test
    void createAthlete_WithCustomClubFullLocation_CreatesUnverifiedClubWithLocation() throws Exception {
        String uniqueClubName = "Escuela Formativa Zuliana " + System.currentTimeMillis();
        String payload = """
                {
                    "firstName": "Sebastian",
                    "lastName": "Perez",
                    "birthDate": "2013-02-14",
                    "primaryPosition": "DELANTERO_CENTRO",
                    "preferredFoot": "IZQUIERDO",
                    "nationality": "Venezuela",
                    "customClubName": "%s",
                    "customClubCity": "Maracaibo",
                    "customClubState": "Zulia",
                    "customClubCountry": "Venezuela"
                }
                """.formatted(uniqueClubName);

        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentClub").exists())
                .andExpect(jsonPath("$.currentClub.name").value(uniqueClubName))
                .andExpect(jsonPath("$.currentClub.city").value("Maracaibo"))
                .andExpect(jsonPath("$.currentClub.state").value("Zulia"))
                .andExpect(jsonPath("$.currentClub.country").value("Venezuela"))
                .andExpect(jsonPath("$.currentClub.verifiedOfficial").value(false));
    }

    @Test
    void createAthlete_WithExistingCustomClubCaseInsensitive_ReusesClub() throws Exception {
        String clubName = "Semillero Barrial " + System.currentTimeMillis();
        String payload1 = """
                {
                    "firstName": "Carlos",
                    "lastName": "Gomez",
                    "birthDate": "2013-05-12",
                    "primaryPosition": "PORTERO",
                    "preferredFoot": "DERECHO",
                    "nationality": "Venezuela",
                    "customClubName": "%s"
                }
                """.formatted(clubName.toLowerCase());

        String res1 = mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Enviar con mayúsculas
        String payload2 = """
                {
                    "firstName": "Daniel",
                    "lastName": "Gomez",
                    "birthDate": "2011-09-22",
                    "primaryPosition": "DEFENSA_CENTRAL",
                    "preferredFoot": "DERECHO",
                    "nationality": "Venezuela",
                    "customClubName": "%s"
                }
                """.formatted(clubName.toUpperCase());

        String res2 = mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + otherTutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.json.JSONObject obj1 = new org.json.JSONObject(res1);
        org.json.JSONObject obj2 = new org.json.JSONObject(res2);

        String clubId1 = obj1.getJSONObject("currentClub").getString("id");
        String clubId2 = obj2.getJSONObject("currentClub").getString("id");

        org.junit.jupiter.api.Assertions.assertEquals(clubId1, clubId2, "El club debe ser reutilizado insensible a mayúsculas");
    }
}
