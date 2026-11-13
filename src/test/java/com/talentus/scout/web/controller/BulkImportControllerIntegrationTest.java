package com.talentus.scout.web.controller;

import com.jayway.jsonpath.JsonPath;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.*;
import com.talentus.scout.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BulkImportControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private BulkImportJobRepository bulkImportJobRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private TutorActivationTokenRepository tutorActivationTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    private ClubOrganization club;
    private User scoutUser;
    private ScoutProfile scoutProfile;
    private String scoutToken;
    private UUID createdJobId;
    private String createdTutorEmail;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long ts = System.currentTimeMillis();
        String clubName = "Club BulkTest " + ts;

        club = clubOrganizationRepository.save(ClubOrganization.builder()
                .name(clubName)
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build());

        scoutUser = userRepository.save(User.builder()
                .email("scout.bulk." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Bulk Integration")
                .phoneNumber("+584120001122")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());

        scoutProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(scoutUser)
                .organizationName(clubName)
                .scoutRole("Coordinador")
                .credentialsDocumentUrl("/uploads/credentials/test-" + ts + ".pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .bulkImportEnabled(true)
                .build());

        scoutToken = jwtService.generateToken(scoutUser);
        createdTutorEmail = "tutor.bulk." + ts + "@talentus.test";
    }

    @AfterEach
    void tearDown() {
        if (createdTutorEmail != null) {
            userRepository.findByEmail(createdTutorEmail).ifPresent(tutor -> {
                athleteRepository.findByTutorIdOrderByCreatedAtDesc(tutor.getId())
                        .forEach(athleteRepository::delete);
                jdbcTemplate.update("DELETE FROM tutor_activation_tokens WHERE tutor_id = ?", tutor.getId());
                userRepository.delete(tutor);
            });
        }
        if (createdJobId != null) {
            bulkImportJobRepository.findById(createdJobId).ifPresent(bulkImportJobRepository::delete);
        }
        if (scoutProfile != null) {
            scoutProfileRepository.delete(scoutProfile);
        }
        if (scoutUser != null) {
            userRepository.delete(scoutUser);
        }
        if (club != null) {
            clubOrganizationRepository.delete(club);
        }
    }

    @Test
    void uploadSingleAthleteCsvShouldCommitAndCompleteAsynchronously() throws Exception {
        String csv = String.join(",", com.talentus.scout.service.bulkimport.BulkImportParser.EXPECTED_HEADERS) + "\n"
                + "Mateo,,Rojas,,V-99112233,2011-08-14,Masculino,mediocampista central,,derecho,Venezuela,168,58,Carlos Rojas,V-12345678," + createdTutorEmail + ",+584140001122\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "atleta_prueba.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        String uploadResp = mockMvc.perform(multipart("/api/v1/scout/bulk-import/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + scoutToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andReturn().getResponse().getContentAsString();

        String jobIdStr = JsonPath.read(uploadResp, "$.id");
        createdJobId = UUID.fromString(jobIdStr);

        String finalStatus = null;
        int successfulRows = 0;
        int failedRows = -1;

        for (int i = 0; i < 30; i++) {
            String pollResp = mockMvc.perform(get("/api/v1/scout/bulk-import/jobs/" + jobIdStr)
                            .header("Authorization", "Bearer " + scoutToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            finalStatus = JsonPath.read(pollResp, "$.status");
            if ("COMPLETED".equals(finalStatus) || "FAILED".equals(finalStatus)) {
                successfulRows = JsonPath.read(pollResp, "$.successfulRows");
                failedRows = JsonPath.read(pollResp, "$.failedRows");
                break;
            }
            Thread.sleep(150);
        }

        assertEquals("COMPLETED", finalStatus, "El BulkImportJob no debe quedarse en PENDING ni fallar por race condition o LazyInitialization");
        assertEquals(1, successfulRows);
        assertEquals(0, failedRows);

        User createdTutor = userRepository.findByEmail(createdTutorEmail).orElse(null);
        assertNotNull(createdTutor);
        assertTrue(athleteRepository.findByTutorIdOrderByCreatedAtDesc(createdTutor.getId()).stream()
                .anyMatch(a -> "V-99112233".equals(a.getIdentityDocumentNumber())
                        && a.getCurrentClub() != null
                        && club.getId().equals(a.getCurrentClub().getId())));
    }
}
