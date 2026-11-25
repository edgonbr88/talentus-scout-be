package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteSubscription;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AthleteSocialLinksIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private AthleteSubscriptionRepository athleteSubscriptionRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private User tutorUser;
    private String tutorToken;
    private User approvedScoutUser;
    private String approvedScoutToken;
    private User pendingScoutUser;
    private String pendingScoutToken;
    private User adminUser;
    private String adminToken;
    private User athleteRoleUser;
    private String athleteRoleToken;
    private SubscriptionPlan proPlan;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long suffix = System.nanoTime();

        tutorUser = userRepository.save(User.builder()
                .email("tutor.social." + suffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Tutor Social")
                .phoneNumber("+584141112233")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());
        tutorToken = jwtService.generateToken(tutorUser);

        approvedScoutUser = userRepository.save(User.builder()
                .email("scout.approved." + suffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Aprobado")
                .phoneNumber("+584142223344")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());
        scoutProfileRepository.save(ScoutProfile.builder()
                .user(approvedScoutUser)
                .organizationName("Club Deportivo Pro")
                .scoutRole("Director de Scouting")
                .credentialsDocumentUrl("https://cdn.talentus.app/scouts/cred-approved.pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .build());
        approvedScoutToken = jwtService.generateToken(approvedScoutUser);

        pendingScoutUser = userRepository.save(User.builder()
                .email("scout.pending." + suffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Pendiente")
                .phoneNumber("+584143334455")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());
        scoutProfileRepository.save(ScoutProfile.builder()
                .user(pendingScoutUser)
                .organizationName("Club Pendiente")
                .scoutRole("Ojeador")
                .credentialsDocumentUrl("https://cdn.talentus.app/scouts/cred-pending.pdf")
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .build());
        pendingScoutToken = jwtService.generateToken(pendingScoutUser);

        adminUser = userRepository.save(User.builder()
                .email("admin.social." + suffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Admin Social")
                .phoneNumber("+584144445566")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
        adminToken = jwtService.generateToken(adminUser);

        athleteRoleUser = userRepository.save(User.builder()
                .email("athlete.role." + suffix + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Atleta Titular")
                .phoneNumber("+584145556677")
                .role(UserRole.ATHLETE)
                .status(AccountStatus.ACTIVE)
                .build());
        athleteRoleToken = jwtService.generateToken(athleteRoleUser);

        proPlan = subscriptionPlanRepository.findById("PLAN_PRO_TEST_TS86")
                .orElseGet(() -> subscriptionPlanRepository.save(SubscriptionPlan.builder()
                        .id("PLAN_PRO_TEST_TS86")
                        .name("Plan Pro TS-86")
                        .priceUsd(new BigDecimal("19.99"))
                        .durationDays(30)
                        .maxVideos(10)
                        .hasRadarViews(true)
                        .hasPdfCv(true)
                        .isActive(true)
                        .build()));
    }

    private Athlete createTestAthlete(String slugPrefix) {
        return athleteRepository.save(Athlete.builder()
                .tutor(tutorUser)
                .firstName("Mateo")
                .lastName("Rivas")
                .birthDate(LocalDate.of(2010, 5, 12))
                .gender("MALE")
                .primaryPosition("Mediocampista Ofensivo")
                .preferredFoot("DERECHO")
                .nationality("Venezolana")
                .heightCm(new BigDecimal("172.00"))
                .weightKg(new BigDecimal("61.50"))
                .bio("Volante creativo con visión de pase.")
                .slug(slugPrefix + "-" + System.nanoTime())
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build());
    }

    private void grantSubscription(Athlete athlete, SubscriptionStatus status, Instant endDate) {
        athleteSubscriptionRepository.save(AthleteSubscription.builder()
                .athlete(athlete)
                .plan(proPlan)
                .startDate(Instant.now().minus(10, ChronoUnit.DAYS))
                .endDate(endDate)
                .status(status)
                .build());
    }

    @Test
    void updateAthlete_WithActiveSubscription_SavesAndClearsValidSocialLinks() throws Exception {
        Athlete athlete = createTestAthlete("mateo-active");
        grantSubscription(athlete, SubscriptionStatus.ACTIVE, Instant.now().plus(20, ChronoUnit.DAYS));

        String savePayload = """
                {
                    "instagramUrl": "https://www.instagram.com/mateorivas10/",
                    "tiktokUrl": "https://www.tiktok.com/@mateorivas10",
                    "youtubeUrl": "https://youtu.be/dQw4w9WgXcQ",
                    "otherVideoPlatformUrl": "https://www.hudl.com/profile/12345/mateo-rivas",
                    "otherVideoPlatformLabel": "Hudl"
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(savePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value("https://www.instagram.com/mateorivas10/"))
                .andExpect(jsonPath("$.tiktokUrl").value("https://www.tiktok.com/@mateorivas10"))
                .andExpect(jsonPath("$.youtubeUrl").value("https://youtu.be/dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.otherVideoPlatformUrl").value("https://www.hudl.com/profile/12345/mateo-rivas"))
                .andExpect(jsonPath("$.otherVideoPlatformLabel").value("Hudl"));

        // Owner sees fields in my-athletes
        mockMvc.perform(get("/api/v1/athletes/my-athletes")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instagramUrl").value("https://www.instagram.com/mateorivas10/"))
                .andExpect(jsonPath("$[0].otherVideoPlatformLabel").value("Hudl"));

        // Owner with active subscription can clear links
        String clearPayload = """
                {
                    "instagramUrl": "",
                    "otherVideoPlatformUrl": ""
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value(nullValue()))
                .andExpect(jsonPath("$.tiktokUrl").value("https://www.tiktok.com/@mateorivas10"))
                .andExpect(jsonPath("$.otherVideoPlatformUrl").value(nullValue()))
                .andExpect(jsonPath("$.otherVideoPlatformLabel").value(nullValue()));
    }

    @Test
    void updateAthlete_WithoutActiveSubscription_BlocksSocialEditButAllowsBasicSportsEdit() throws Exception {
        Athlete athlete = createTestAthlete("mateo-inactive");

        // Trying to add social link without Pro subscription -> 403 Forbidden
        String socialPayload = """
                {
                    "instagramUrl": "https://instagram.com/mateo10"
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(socialPayload))
                .andExpect(status().isForbidden());

        // Editing basic sports info without touching social links -> 200 OK
        String basicPayload = """
                {
                    "bio": "Bio actualizada sin plan Pro",
                    "heightCm": 175.00
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basicPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Bio actualizada sin plan Pro"))
                .andExpect(jsonPath("$.heightCm").value(175.00));
    }

    @Test
    void updateAthlete_WithExpiredSubscription_KeepsStoredLinksVisibleToScoutAndBlocksModifications() throws Exception {
        Athlete athlete = createTestAthlete("mateo-expired");
        athlete.setInstagramUrl("https://instagram.com/mateo_previo");
        athlete.setYoutubeUrl("https://www.youtube.com/@mateo_previo");
        athleteRepository.save(athlete);

        grantSubscription(athlete, SubscriptionStatus.ACTIVE, Instant.now().minus(2, ChronoUnit.DAYS));

        // Modifying social links when expired -> 403 Forbidden
        String modifyPayload = """
                {
                    "instagramUrl": "https://instagram.com/mateo_nuevo"
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyPayload))
                .andExpect(status().isForbidden());

        // Basic edit keeping same stored social values -> 200 OK and links remain intact
        String sportsOnlyPayload = """
                {
                    "bio": "Actualización tras vencimiento",
                    "instagramUrl": "https://instagram.com/mateo_previo",
                    "youtubeUrl": "https://www.youtube.com/@mateo_previo"
                }
                """;

        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sportsOnlyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/mateo_previo"));

        // Approved scout still sees stored links even after expiration
        mockMvc.perform(get("/api/v1/athletes/public/" + athlete.getSlug())
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks.instagramUrl").value("https://instagram.com/mateo_previo"))
                .andExpect(jsonPath("$.socialLinks.youtubeUrl").value("https://www.youtube.com/@mateo_previo"));
    }

    @Test
    void updateAthlete_WithInvalidUrlsOrDomains_Returns400BadRequest() throws Exception {
        Athlete athlete = createTestAthlete("mateo-invalid-urls");
        grantSubscription(athlete, SubscriptionStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.DAYS));

        // Non-https protocol
        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instagramUrl\":\"http://instagram.com/mateo\"}"))
                .andExpect(status().isBadRequest());

        // javascript: protocol injection
        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherVideoPlatformUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());

        // Wrong domain for Instagram
        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instagramUrl\":\"https://notinstagram.com/mateo\"}"))
                .andExpect(status().isBadRequest());

        // Wrong domain for TikTok
        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tiktokUrl\":\"https://vimeo.com/123456\"}"))
                .andExpect(status().isBadRequest());

        // Wrong domain for YouTube
        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"youtubeUrl\":\"https://tiktok.com/@mateo\"}"))
                .andExpect(status().isBadRequest());

        // Label without URL
        mockMvc.perform(put("/api/v1/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherVideoPlatformLabel\":\"Hudl\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicAthleteBySlug_EnforcesLopnnaSocialLinksVisibility() throws Exception {
        Athlete athleteWithLinks = createTestAthlete("mateo-lopnna");
        athleteWithLinks.setInstagramUrl("https://instagram.com/mateo_oficial");
        athleteWithLinks.setTiktokUrl("https://vm.tiktok.com/ZM12345/");
        athleteWithLinks.setYoutubeUrl("https://www.youtube.com/watch?v=abc123");
        athleteWithLinks.setOtherVideoPlatformUrl("https://app.veo.co/matches/123");
        athleteWithLinks.setOtherVideoPlatformLabel("Veo");
        athleteRepository.save(athleteWithLinks);

        // Anonymous visitor -> socialLinks MUST be null
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteWithLinks.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks").value(nullValue()));

        // Authenticated TUTOR -> socialLinks MUST be null
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteWithLinks.getSlug())
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks").value(nullValue()));

        // Authenticated ATHLETE -> socialLinks MUST be null
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteWithLinks.getSlug())
                        .header("Authorization", "Bearer " + athleteRoleToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks").value(nullValue()));

        // Authenticated PENDING SCOUT -> socialLinks MUST be null
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteWithLinks.getSlug())
                        .header("Authorization", "Bearer " + pendingScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks").value(nullValue()));

        // Authenticated APPROVED SCOUT -> socialLinks populated
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteWithLinks.getSlug())
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks.instagramUrl").value("https://instagram.com/mateo_oficial"))
                .andExpect(jsonPath("$.socialLinks.tiktokUrl").value("https://vm.tiktok.com/ZM12345/"))
                .andExpect(jsonPath("$.socialLinks.youtubeUrl").value("https://www.youtube.com/watch?v=abc123"))
                .andExpect(jsonPath("$.socialLinks.otherPlatformUrl").value("https://app.veo.co/matches/123"))
                .andExpect(jsonPath("$.socialLinks.otherPlatformLabel").value("Veo"));

        // Authenticated ADMIN -> socialLinks populated
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteWithLinks.getSlug())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks.instagramUrl").value("https://instagram.com/mateo_oficial"))
                .andExpect(jsonPath("$.socialLinks.otherPlatformLabel").value("Veo"));

        // Athlete without any links -> socialLinks is null even for approved scout
        Athlete athleteNoLinks = createTestAthlete("mateo-nolinks");
        mockMvc.perform(get("/api/v1/athletes/public/" + athleteNoLinks.getSlug())
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks").value(nullValue()));
    }
}
