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
}
