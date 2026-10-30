package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.*;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.CreateTokenCertificationRequest;
import com.talentus.scout.web.dto.SubmitTokenCertificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CertificationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private TutorLegalConsentRepository tutorLegalConsentRepository;

    @Autowired
    private CertificationRequestRepository certificationRequestRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String tutorToken;
    private String adminToken;
    private User tutorUser;
    private Athlete athlete;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        tutorUser = userRepository.save(User.builder()
                .email("tutor.cert." + uniqueSuffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Carlos Mendoza")
                .phoneNumber("+584121234567")
                .role(UserRole.TUTOR)
                .build());

        tutorLegalConsentRepository.save(TutorLegalConsent.builder()
                .tutor(tutorUser)
                .acceptedLopnnaTerms(true)
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .build());

        User adminUser = userRepository.save(User.builder()
                .email("admin.cert." + uniqueSuffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Admin Cert")
                .phoneNumber("+584149998877")
                .role(UserRole.ADMIN)
                .build());

        athlete = athleteRepository.save(Athlete.builder()
                .tutor(tutorUser)
                .firstName("Santiago")
                .lastName("Mendoza")
                .birthDate(LocalDate.of(2010, 4, 12))
                .gender("MALE")
                .primaryPosition("DELANTERO_CENTRO")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("santiago-mendoza-cert-" + uniqueSuffix)
                .isPublic(true)
                .legalVerificationStatus(com.talentus.scout.domain.enums.LegalVerificationStatus.APPROVED)
                .build());

        tutorToken = jwtService.generateToken(tutorUser);
        adminToken = jwtService.generateToken(adminUser);
    }

    @Test
    @DisplayName("Flujo completo: Tutor crea token -> Certificador anónimo consulta y aprueba instantáneamente")
    void tokenCertificationFlow_FullLifecycle_AnonymousAccess() throws Exception {
        // 1. Tutor solicita certificación con enlace/token
        CreateTokenCertificationRequest createReq = CreateTokenCertificationRequest.builder()
                .athleteId(athlete.getId())
                .badgeType(BadgeType.MEDICAL_ANTHRO)
                .title("Evaluación Antropométrica 2026")
                .description("Control biométrico semestral")
                .targetEmail("dr.gonzalez@clinicaccs.com")
                .targetName("Dr. Pedro González")
                .targetOrganization("Policlínica Metropolitana")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/certifications/request-token")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.tokenHash", notNullValue()))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String tokenHash = objectMapper.readTree(responseBody).get("tokenHash").asText();

        // 2. Certificador externo abre el enlace públicamente (SIN encabezado de autenticación)
        mockMvc.perform(get("/api/v1/certifications/token/" + tokenHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteFullName", is("Santiago Mendoza")))
                .andExpect(jsonPath("$.targetEmail", is("dr.gonzalez@clinicaccs.com")))
                .andExpect(jsonPath("$.badgeType", is("MEDICAL_ANTHRO")))
                .andExpect(jsonPath("$.alreadyCompleted", is(false)));

        // 3. Certificador externo envía el formulario firmado (SIN autenticación)
        SubmitTokenCertificationRequest submitReq = SubmitTokenCertificationRequest.builder()
                .certifierFullName("Dr. Pedro González")
                .certifierLicenseId("CMD-12345")
                .certifierNotes("Estatura y peso medidos en ayunas")
                .heightCm(BigDecimal.valueOf(174.5))
                .weightKg(BigDecimal.valueOf(63.2))
                .bodyFatPercent(BigDecimal.valueOf(10.8))
                .build();

        mockMvc.perform(post("/api/v1/certifications/token/" + tokenHash + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.certifierFullName", is("Dr. Pedro González")))
                .andExpect(jsonPath("$.badgeId", notNullValue()));

        // 4. Perfil público del atleta ahora refleja la biometría certificada
        mockMvc.perform(get("/api/v1/athletes/public/" + athlete.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.medicalCertified", is(true)))
                .andExpect(jsonPath("$.badges", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Admin puede listar solicitudes pendientes pero un Tutor recibe 403 Forbidden")
    void adminEndpoints_RoleVerification() throws Exception {
        // Admin accede a pending
        mockMvc.perform(get("/api/v1/admin/certifications/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Tutor recibe 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/certifications/pending")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());
    }
}
