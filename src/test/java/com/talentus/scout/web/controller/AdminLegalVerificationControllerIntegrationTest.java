package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.legal.ReviewLegalVerificationRequest;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminLegalVerificationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private TutorLegalConsentRepository tutorLegalConsentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private User adminUser;
    private String adminToken;

    private User tutorUser;
    private String tutorToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long timestamp = System.currentTimeMillis();

        adminUser = User.builder()
                .email("admin.legal." + timestamp + "@talentus.app")
                .passwordHash(passwordEncoder.encode("AdminPass123!"))
                .fullName("Auditor Legal Admin")
                .phoneNumber("+584140001122")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        tutorUser = User.builder()
                .email("tutor.legal." + timestamp + "@talentus.app")
                .passwordHash(passwordEncoder.encode("TutorPass123!"))
                .fullName("Carlos Pérez Tutor")
                .phoneNumber("+584143334455")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        tutorUser = userRepository.save(tutorUser);
        tutorToken = jwtService.generateToken(tutorUser);

        TutorLegalConsent consent = TutorLegalConsent.builder()
                .tutor(tutorUser)
                .acceptedLopnnaTerms(true)
                .termsVersion("1.0")
                .ipAddress("127.0.0.1")
                .userAgent("TestAgent")
                .idDocumentType("CEDULA")
                .idDocumentNumber("V-18765432")
                .idDocumentFileUrl("https://storage.talentus.app/identity-documents/tutors/cedula_tutor.pdf")
                .build();
        tutorLegalConsentRepository.save(consent);
    }

    private Athlete createTestAthlete(String slugSuffix, LegalVerificationStatus status) {
        Athlete athlete = Athlete.builder()
                .tutor(tutorUser)
                .firstName("Mateo")
                .lastName("Pérez")
                .birthDate(LocalDate.of(2011, 4, 18))
                .gender("MALE")
                .primaryPosition("DELANTERO")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("mateo-perez-" + slugSuffix)
                .isPublic(true)
                .identityDocumentType("PARTIDA_NACIMIENTO")
                .identityDocumentNumber("PN-2011-8899")
                .identityDocumentFileUrl("https://storage.talentus.app/identity-documents/athletes/partida_mateo.pdf")
                .legalVerificationStatus(status)
                .build();
        return athleteRepository.save(athlete);
    }

    @Test
    void getPendingVerifications_AsAdmin_ShouldReturn200AndPage() throws Exception {
        Athlete athlete = createTestAthlete("pending-" + System.currentTimeMillis(), LegalVerificationStatus.PENDING_REVIEW);

        mockMvc.perform(get("/api/v1/admin/legal-verifications/pending")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[?(@.athleteId == '" + athlete.getId() + "')].athleteFullName").value("Mateo Pérez"))
                .andExpect(jsonPath("$.content[?(@.athleteId == '" + athlete.getId() + "')].tutorFullName").value("Carlos Pérez Tutor"))
                .andExpect(jsonPath("$.content[?(@.athleteId == '" + athlete.getId() + "')].athleteDocType").value("PARTIDA_NACIMIENTO"))
                .andExpect(jsonPath("$.content[?(@.athleteId == '" + athlete.getId() + "')].tutorDocType").value("CEDULA"));
    }

    @Test
    void getPendingVerifications_AsTutor_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/legal-verifications/pending")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVerificationMetrics_AsAdmin_ShouldReturnMetrics() throws Exception {
        createTestAthlete("metrics-" + System.currentTimeMillis(), LegalVerificationStatus.PENDING_REVIEW);

        mockMvc.perform(get("/api/v1/admin/legal-verifications/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.approvedCount").isNumber())
                .andExpect(jsonPath("$.rejectedCount").isNumber());
    }

    @Test
    void getVerificationDetail_AsAdmin_ShouldReturnDetail() throws Exception {
        Athlete athlete = createTestAthlete("detail-" + System.currentTimeMillis(), LegalVerificationStatus.PENDING_REVIEW);

        mockMvc.perform(get("/api/v1/admin/legal-verifications/" + athlete.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteId").value(athlete.getId().toString()))
                .andExpect(jsonPath("$.athleteFullName").value("Mateo Pérez"))
                .andExpect(jsonPath("$.tutorEmail").value(tutorUser.getEmail()))
                .andExpect(jsonPath("$.athleteDocFileUrl").value("https://storage.talentus.app/identity-documents/athletes/partida_mateo.pdf"))
                .andExpect(jsonPath("$.tutorDocFileUrl").value("https://storage.talentus.app/identity-documents/tutors/cedula_tutor.pdf"));
    }

    @Test
    void reviewVerification_Approve_ShouldSetStatusApprovedAndAuditFields() throws Exception {
        Athlete athlete = createTestAthlete("approve-" + System.currentTimeMillis(), LegalVerificationStatus.PENDING_REVIEW);

        ReviewLegalVerificationRequest request = ReviewLegalVerificationRequest.builder()
                .decision(LegalVerificationStatus.APPROVED)
                .notes("Documentación cotejada con éxito. Filiación verificada.")
                .build();

        mockMvc.perform(post("/api/v1/admin/legal-verifications/" + athlete.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteId").value(athlete.getId().toString()))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.verifiedByEmail").value(adminUser.getEmail()));

        Athlete updated = athleteRepository.findById(athlete.getId()).orElseThrow();
        assertThat(updated.getLegalVerificationStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
        assertThat(updated.getLegalVerifiedBy().getId()).isEqualTo(adminUser.getId());
        assertThat(updated.getLegalVerifiedAt()).isNotNull();
    }

    @Test
    void reviewVerification_RejectWithoutNotes_ShouldReturn400BadRequest() throws Exception {
        Athlete athlete = createTestAthlete("reject-nonotes-" + System.currentTimeMillis(), LegalVerificationStatus.PENDING_REVIEW);

        ReviewLegalVerificationRequest request = ReviewLegalVerificationRequest.builder()
                .decision(LegalVerificationStatus.REJECTED)
                .notes("   ")
                .build();

        mockMvc.perform(post("/api/v1/admin/legal-verifications/" + athlete.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewVerification_RejectWithNotes_ShouldSetStatusRejectedAndAuditFields() throws Exception {
        Athlete athlete = createTestAthlete("reject-notes-" + System.currentTimeMillis(), LegalVerificationStatus.PENDING_REVIEW);

        ReviewLegalVerificationRequest request = ReviewLegalVerificationRequest.builder()
                .decision(LegalVerificationStatus.REJECTED)
                .notes("La copia de la partida de nacimiento se encuentra cortada y no se aprecian los nombres de los progenitores.")
                .build();

        mockMvc.perform(post("/api/v1/admin/legal-verifications/" + athlete.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.notes").value(request.getNotes()));

        Athlete updated = athleteRepository.findById(athlete.getId()).orElseThrow();
        assertThat(updated.getLegalVerificationStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
        assertThat(updated.getLegalVerificationNotes()).isEqualTo(request.getNotes());
        assertThat(updated.getLegalVerifiedBy().getId()).isEqualTo(adminUser.getId());
    }
}
