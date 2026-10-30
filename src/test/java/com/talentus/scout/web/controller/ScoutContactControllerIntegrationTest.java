package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.*;
import com.talentus.scout.repository.*;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.ContactClickRequest;
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
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ScoutContactControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private ScoutActivityLogRepository scoutActivityLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String approvedScoutToken;
    private String tutorToken;
    private User tutorUser;
    private Athlete athlete;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // 1. Crear Tutor
        String tutorEmail = "tutor.radar." + UUID.randomUUID() + "@talentus.app";
        tutorUser = new User(
                tutorEmail,
                passwordEncoder.encode("Password123*"),
                "Roberto Morales",
                "+584121234567",
                UserRole.TUTOR,
                AccountStatus.ACTIVE
        );
        tutorUser = userRepository.save(tutorUser);
        tutorToken = jwtService.generateToken(tutorUser);

        // 2. Crear Atleta del Tutor
        athlete = Athlete.builder()
                .tutor(tutorUser)
                .firstName("Luis")
                .lastName("Morales")
                .birthDate(LocalDate.of(2009, 3, 21))
                .primaryPosition("MEDIOCAMPISTA_OFENSIVO")
                .preferredFoot("DIESTRO")
                .slug("luis-morales-" + UUID.randomUUID().toString().substring(0, 8))
                .isPublic(true)
                .build();
        athlete = athleteRepository.save(athlete);

        // 3. Crear Scout Aprobado
        String scoutEmail = "scout.radar." + UUID.randomUUID() + "@deportivomiranda.com";
        User scoutUser = new User(
                scoutEmail,
                passwordEncoder.encode("Password123*"),
                "Andrés Bello",
                "+584145556677",
                UserRole.SCOUT,
                AccountStatus.ACTIVE
        );
        scoutUser = userRepository.save(scoutUser);

        ScoutProfile scoutProfile = ScoutProfile.builder()
                .user(scoutUser)
                .organizationName("Deportivo Miranda")
                .scoutRole("Jefe de Captación Juvenil")
                .credentialsDocumentUrl("https://storage.talentus.app/scouts/doc.pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .build();
        scoutProfileRepository.save(scoutProfile);

        approvedScoutToken = jwtService.generateToken(scoutUser);
    }

    @Test
    @DisplayName("POST /api/v1/scouts/contact-click con scout aprobado debe generar URL de WhatsApp y registrar log")
    void contactClick_ApprovedScout_Success() throws Exception {
        ContactClickRequest request = ContactClickRequest.builder()
                .athleteId(athlete.getId())
                .build();

        mockMvc.perform(post("/api/v1/scouts/contact-click")
                        .header("Authorization", "Bearer " + approvedScoutToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorName").value("Roberto Morales"))
                .andExpect(jsonPath("$.athleteName").value("Luis Morales"))
                .andExpect(jsonPath("$.whatsappUrl", startsWith("https://wa.me/584121234567?text=")))
                .andExpect(jsonPath("$.whatsappUrl", containsString("Roberto%20Morales")))
                .andExpect(jsonPath("$.whatsappUrl", containsString("Andr%C3%A9s%20Bello")))
                .andExpect(jsonPath("$.whatsappUrl", containsString("Deportivo%20Miranda")))
                .andExpect(jsonPath("$.whatsappUrl", containsString("Luis%20Morales")));
    }

    @Test
    @DisplayName("POST /api/v1/scouts/contact-click con rol TUTOR debe ser rechazado con 403 Forbidden")
    void contactClick_ForbiddenForTutor() throws Exception {
        ContactClickRequest request = ContactClickRequest.builder()
                .athleteId(athlete.getId())
                .build();

        mockMvc.perform(post("/api/v1/scouts/contact-click")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/athletes/radar con token de tutor debe retornar métricas y actividades")
    void getTutorRadar_Success() throws Exception {
        // Ejecutar un contact-click previo para tener actividad
        ContactClickRequest request = ContactClickRequest.builder()
                .athleteId(athlete.getId())
                .build();

        mockMvc.perform(post("/api/v1/scouts/contact-click")
                        .header("Authorization", "Bearer " + approvedScoutToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Consultar radar del tutor
        mockMvc.perform(get("/api/v1/athletes/radar")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContactClicks", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recentInteractions", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.recentInteractions[0].scoutClub").value("Deportivo Miranda"))
                .andExpect(jsonPath("$.athletesMetrics", hasSize(1)))
                .andExpect(jsonPath("$.athletesMetrics[0].athleteName").value("Luis Morales"));
    }
}
