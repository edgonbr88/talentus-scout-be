package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ScoutDirectorySecurityTest {

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

    private MockMvc mockMvc;

    private String pendingScoutToken;
    private String approvedScoutToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long time = System.currentTimeMillis();

        // 1. Scout Pendiente de Verificación
        User pendingUser = User.builder()
                .email("scout.pending." + time + "@scouting.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Pendiente")
                .phoneNumber("+584121112233")
                .role(UserRole.SCOUT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();
        pendingUser = userRepository.save(pendingUser);
        pendingScoutToken = jwtService.generateToken(pendingUser);

        ScoutProfile pendingProfile = ScoutProfile.builder()
                .user(pendingUser)
                .organizationName("Club Deportivo Pendiente")
                .scoutRole("Reclutador")
                .credentialsDocumentUrl("/uploads/credentials/cred-pending.pdf")
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .build();
        scoutProfileRepository.save(pendingProfile);

        // 2. Scout Aprobado
        User approvedUser = User.builder()
                .email("scout.approved." + time + "@scouting.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Oficial Aprobado")
                .phoneNumber("+584129998877")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build();
        approvedUser = userRepository.save(approvedUser);
        approvedScoutToken = jwtService.generateToken(approvedUser);

        ScoutProfile approvedProfile = ScoutProfile.builder()
                .user(approvedUser)
                .organizationName("Sevilla FC Scouting")
                .scoutRole("Ojeador Principal")
                .credentialsDocumentUrl("/uploads/credentials/cred-approved.pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .build();
        scoutProfileRepository.save(approvedProfile);

        // 3. Usuario Administrador
        User adminUser = User.builder()
                .email("admin." + time + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Administrador General")
                .phoneNumber("+584120000000")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);
    }

    @Test
    void getDirectory_WithoutToken_ReturnsUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void getDirectory_AsPendingScout_Returns403ForbiddenWithAuditMessage() throws Exception {
        // Criterio de Aceptación (DoD):
        // "Si un scout pendiente intenta consultar el directorio, recibe 403 Forbidden con mensaje de auditoría en curso"
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .header("Authorization", "Bearer " + pendingScoutToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Auditoría en curso")));
    }

    @Test
    void getDirectory_AsApprovedScout_Returns200Ok() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getDirectory_AsAdmin_Returns200Ok() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
