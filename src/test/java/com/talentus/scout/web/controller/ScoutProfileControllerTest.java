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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ScoutProfileControllerTest {

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

    private User scoutUser;
    private ScoutProfile scoutProfile;
    private String scoutToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String uniqueEmail = "scout.test." + UUID.randomUUID() + "@club.com";

        scoutUser = User.builder()
                .email(uniqueEmail)
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Subsanador Test")
                .phoneNumber("+584129998877")
                .role(UserRole.SCOUT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();
        userRepository.save(scoutUser);

        scoutProfile = ScoutProfile.builder()
                .user(scoutUser)
                .organizationName("Club Deportivo Test")
                .scoutRole("Reclutador Oficial")
                .credentialsDocumentUrl("/uploads/credentials/old_cred.pdf")
                .verificationStatus(ScoutVerificationStatus.REJECTED)
                .rejectionReason("Carnet borroso o vencido")
                .build();
        scoutProfileRepository.save(scoutProfile);

        scoutToken = jwtService.generateToken(scoutUser);
    }

    @Test
    void reUploadCredentials_debeActualizarCredencialYRetornarEstadoPending() throws Exception {
        MockMultipartFile newCredentialsFile = new MockMultipartFile(
                "credentialsFile",
                "credencial_subsanada.pdf",
                "application/pdf",
                "Contenido de credencial corregida".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/scout/re-upload-credentials")
                        .file(newCredentialsFile)
                        .header("Authorization", "Bearer " + scoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.credentialsDocumentUrl").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Credenciales actualizadas exitosamente. Tu perfil ha retornado al estado de auditoría."));

        ScoutProfile updated = scoutProfileRepository.findById(scoutProfile.getId()).orElseThrow();
        assertThat(updated.getVerificationStatus()).isEqualTo(ScoutVerificationStatus.PENDING);
        assertThat(updated.getRejectionReason()).isNull();
        assertThat(updated.getCredentialsDocumentUrl()).contains(".pdf");
    }

    @Test
    void reUploadCredentials_sinAutenticacion_debeRetornarForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "credentialsFile",
                "cred.pdf",
                "application/pdf",
                "test".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/scout/re-upload-credentials")
                        .file(file))
                .andExpect(status().isForbidden());
    }
}
