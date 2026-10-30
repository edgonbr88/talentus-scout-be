package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.ReviewScoutDecision;
import com.talentus.scout.web.dto.ReviewScoutRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminScoutWorkflowTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private User adminUser;
    private String adminToken;

    private User scoutUser;
    private ScoutProfile scoutProfile;
    private String scoutToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long time = System.currentTimeMillis();

        // 1. Admin User
        adminUser = User.builder()
                .email("admin." + time + "@talentus.test")
                .passwordHash(passwordEncoder.encode("AdminPass123*"))
                .fullName("Administrador General")
                .phoneNumber("+584120001122")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        // 2. Scout Pendiente
        scoutUser = User.builder()
                .email("scout.audit." + time + "@scouting.test")
                .passwordHash(passwordEncoder.encode("ScoutPass123*"))
                .fullName("Reclutador Audit")
                .phoneNumber("+584129998877")
                .role(UserRole.SCOUT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();
        scoutUser = userRepository.save(scoutUser);
        scoutToken = jwtService.generateToken(scoutUser);

        scoutProfile = ScoutProfile.builder()
                .user(scoutUser)
                .organizationName("Academia Internacional")
                .scoutRole("Director Deportivo")
                .credentialsDocumentUrl("/uploads/credentials/cred-" + time + ".pdf")
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .build();
        scoutProfile = scoutProfileRepository.save(scoutProfile);
    }

    @Test
    void getPendingScouts_AsAdmin_Returns200AndPendingList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/scouts/pending")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[*].scoutProfileId", hasItem(scoutProfile.getId().toString())))
                .andExpect(jsonPath("$.content[*].verificationStatus", hasItem("PENDING")))
                .andExpect(jsonPath("$.content[*].credentialsDocumentUrl", hasItem(scoutProfile.getCredentialsDocumentUrl())));
    }

    @Test
    void getPendingScouts_AsScout_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/scouts/pending")
                        .header("Authorization", "Bearer " + scoutToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPendingScouts_Unauthenticated_ReturnsForbiddenOrUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/scouts/pending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewScout_ApproveDecision_UpdatesStatusToApprovedAndUserToActive() throws Exception {
        ReviewScoutRequest request = ReviewScoutRequest.builder()
                .decision(ReviewScoutDecision.APPROVE)
                .build();

        mockMvc.perform(post("/api/v1/admin/scouts/" + scoutProfile.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoutProfileId", is(scoutProfile.getId().toString())))
                .andExpect(jsonPath("$.verificationStatus", is("APPROVED")))
                .andExpect(jsonPath("$.accountStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.reviewedByAdminId", is(adminUser.getId().toString())))
                .andExpect(jsonPath("$.reviewedByAdminName", is("Administrador General")));

        // Validar persistencia en BD
        ScoutProfile updatedProfile = scoutProfileRepository.findById(scoutProfile.getId()).orElseThrow();
        assertThat(updatedProfile.getVerificationStatus()).isEqualTo(ScoutVerificationStatus.APPROVED);
        assertThat(updatedProfile.getReviewedByAdmin()).isNotNull();
        assertThat(updatedProfile.getReviewedByAdmin().getId()).isEqualTo(adminUser.getId());
        assertThat(updatedProfile.getReviewedAt()).isNotNull();
        assertThat(updatedProfile.getRejectionReason()).isNull();

        User updatedUser = userRepository.findById(scoutUser.getId()).orElseThrow();
        assertThat(updatedUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void reviewScout_RejectDecisionWithReason_UpdatesStatusToRejectedAndKeepsUserPending() throws Exception {
        ReviewScoutRequest request = ReviewScoutRequest.builder()
                .decision(ReviewScoutDecision.REJECT)
                .rejectionReason("Carnet profesional vencido y sin sello legible")
                .build();

        mockMvc.perform(post("/api/v1/admin/scouts/" + scoutProfile.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoutProfileId", is(scoutProfile.getId().toString())))
                .andExpect(jsonPath("$.verificationStatus", is("REJECTED")))
                .andExpect(jsonPath("$.accountStatus", is("PENDING_VERIFICATION")))
                .andExpect(jsonPath("$.rejectionReason", is("Carnet profesional vencido y sin sello legible")));

        // Validar persistencia en BD
        ScoutProfile updatedProfile = scoutProfileRepository.findById(scoutProfile.getId()).orElseThrow();
        assertThat(updatedProfile.getVerificationStatus()).isEqualTo(ScoutVerificationStatus.REJECTED);
        assertThat(updatedProfile.getRejectionReason()).isEqualTo("Carnet profesional vencido y sin sello legible");

        User updatedUser = userRepository.findById(scoutUser.getId()).orElseThrow();
        assertThat(updatedUser.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        // Validar que el scout puede autenticarse y consultar /me para ver el motivo del rechazo
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + scoutToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoutVerificationStatus", is("REJECTED")))
                .andExpect(jsonPath("$.rejectionReason", is("Carnet profesional vencido y sin sello legible")));
    }

    @Test
    void reviewScout_RejectDecisionWithoutReason_Returns400BadRequest() throws Exception {
        ReviewScoutRequest request = ReviewScoutRequest.builder()
                .decision(ReviewScoutDecision.REJECT)
                .rejectionReason("")
                .build();

        mockMvc.perform(post("/api/v1/admin/scouts/" + scoutProfile.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("El motivo de rechazo es obligatorio")));
    }

    @Test
    void reviewScout_NonExistentProfile_Returns404NotFound() throws Exception {
        ReviewScoutRequest request = ReviewScoutRequest.builder()
                .decision(ReviewScoutDecision.APPROVE)
                .build();

        mockMvc.perform(post("/api/v1/admin/scouts/" + UUID.randomUUID() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
