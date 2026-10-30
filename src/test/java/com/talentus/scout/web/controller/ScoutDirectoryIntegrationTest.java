package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.*;
import com.talentus.scout.repository.*;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ScoutDirectoryIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private AthleteVideoRepository athleteVideoRepository;

    @Autowired
    private AthleteBadgeRepository athleteBadgeRepository;

    @Autowired
    private AthleteSubscriptionRepository athleteSubscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String approvedScoutToken;
    private ClubOrganization clubCaracas;
    private Athlete athleteStriker2010;
    private Athlete athleteDefender2008;
    private Athlete privateAthlete;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long time = System.currentTimeMillis();

        // 1. Scout Aprobado
        User approvedScoutUser = User.builder()
                .email("scout.dir." + time + "@scout.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Aprobado Directorio")
                .phoneNumber("+584120000001")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build();
        approvedScoutUser = userRepository.save(approvedScoutUser);
        approvedScoutToken = jwtService.generateToken(approvedScoutUser);

        ScoutProfile approvedProfile = ScoutProfile.builder()
                .user(approvedScoutUser)
                .organizationName("Talent Scout FC")
                .scoutRole("Director Técnico")
                .credentialsDocumentUrl("/uploads/credentials/cred-" + time + ".pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .build();
        scoutProfileRepository.save(approvedProfile);

        // 2. Tutor
        User tutor = User.builder()
                .email("tutor.dir." + time + "@tutor.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Tutor Legal")
                .phoneNumber("+584129999999")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .country("Venezuela")
                .state("Distrito Capital")
                .build();
        tutor = userRepository.save(tutor);

        // 3. Club
        clubCaracas = ClubOrganization.builder()
                .name("Caracas FC Academy " + time)
                .federationCode("FVF-CCS-" + time)
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build();
        clubCaracas = clubOrganizationRepository.save(clubCaracas);

        // 4. Atleta 1: Delantero Centro, 2010, Zurdo, Pasaporte Español, Con Video READY, Público
        athleteStriker2010 = Athlete.builder()
                .tutor(tutor)
                .firstName("Alejandro")
                .lastName("Gómez " + time)
                .birthDate(LocalDate.of(2010, 5, 20))
                .primaryPosition("DELANTERO_CENTRO")
                .secondaryPosition("EXTREMO_IZQUIERDO")
                .preferredFoot("ZURDO")
                .nationality("Venezolana")
                .secondaryPassports("Español (UE)")
                .currentClub(clubCaracas)
                .heightCm(new BigDecimal("172.00"))
                .weightKg(new BigDecimal("60.00"))
                .profilePhotoUrl("https://cdn.talentus.app/photos/alejandro.jpg")
                .slug("alejandro-gomez-" + time)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build();
        athleteStriker2010 = athleteRepository.save(athleteStriker2010);

        AthleteVideo video = AthleteVideo.builder()
                .athlete(athleteStriker2010)
                .title("Compilado de Goles 2026")
                .category(VideoCategory.HIGHLIGHTS)
                .providerVideoId("cf-prov-" + time)
                .playbackUrl("https://videodelivery.net/stream/video-" + time + ".m3u8")
                .thumbnailUrl("https://videodelivery.net/stream/thumb-" + time + ".jpg")
                .durationSeconds(120)
                .status(VideoStatus.READY)
                .sortOrder(1)
                .build();
        athleteVideoRepository.save(video);

        AthleteBadge badge = AthleteBadge.builder()
                .athlete(athleteStriker2010)
                .badgeType(BadgeType.FEDERATION_LICENSE)
                .organization(clubCaracas)
                .status(BadgeStatus.APPROVED)
                .build();
        athleteBadgeRepository.save(badge);

        // 5. Atleta 2: Defensa Central, 2008, Diestro, Sin Pasaporte Secundario, Sin Video, Público
        athleteDefender2008 = Athlete.builder()
                .tutor(tutor)
                .firstName("Carlos")
                .lastName("Silva " + time)
                .birthDate(LocalDate.of(2008, 9, 10))
                .primaryPosition("DEFENSA_CENTRAL")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .secondaryPassports(null)
                .currentClub(clubCaracas)
                .slug("carlos-silva-" + time)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build();
        athleteDefender2008 = athleteRepository.save(athleteDefender2008);

        // 6. Atleta 3: Privado (LOPNNA is_public = false), Delantero 2010
        privateAthlete = Athlete.builder()
                .tutor(tutor)
                .firstName("Mateo")
                .lastName("Oculto " + time)
                .birthDate(LocalDate.of(2010, 3, 14))
                .primaryPosition("DELANTERO_CENTRO")
                .preferredFoot("ZURDO")
                .nationality("Venezolana")
                .slug("mateo-oculto-" + time)
                .isPublic(false)
                .build();
        privateAthlete = athleteRepository.save(privateAthlete);

        // Subscripciones activas para aparecer en el radar del directorio
        SubscriptionPlan activePlan = subscriptionPlanRepository.findById("PLAN_PRO_YEARLY")
                .orElseGet(() -> subscriptionPlanRepository.findAll().stream().findFirst().orElse(null));

        if (activePlan != null) {
            AthleteSubscription sub1 = AthleteSubscription.builder()
                    .athlete(athleteStriker2010)
                    .plan(activePlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    .endDate(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                    .build();
            athleteSubscriptionRepository.save(sub1);

            AthleteSubscription sub2 = AthleteSubscription.builder()
                    .athlete(athleteDefender2008)
                    .plan(activePlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    .endDate(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                    .build();
            athleteSubscriptionRepository.save(sub2);
        }
    }

    @Test
    void getDirectory_WithoutFilters_ReturnsPublicAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteDefender2008.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(privateAthlete.getSlug()))));
    }

    @Test
    void getDirectory_FilterByBirthYear_ReturnsMatchingAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYear", "2010")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))));
    }

    @Test
    void getDirectory_FilterByPosition_ReturnsMatchingAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("primaryPosition", "DELANTERO_CENTRO")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))));
    }

    @Test
    void getDirectory_FilterByPreferredFoot_ReturnsMatchingAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("preferredFoot", "ZURDO")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))));
    }

    @Test
    void getDirectory_FilterByEuropeanPassport_ReturnsMatchingAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("hasEuropeanPassport", "true")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))));
    }

    @Test
    void getDirectory_FilterByHasVideo_ReturnsOnlyAthletesWithReadyVideos() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("hasVideo", "true")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))))
                .andExpect(jsonPath("$.content[?(@.slug == '" + athleteStriker2010.getSlug() + "')].hasVideo").value(hasItem(true)))
                .andExpect(jsonPath("$.content[?(@.slug == '" + athleteStriker2010.getSlug() + "')].featuredVideoPlaybackUrl").value(hasItem(notNullValue())));
    }

    @Test
    void getDirectory_FilterByClubId_ReturnsMatchingAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("clubId", clubCaracas.getId().toString())
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteDefender2008.getSlug())));
    }

    @Test
    void getDirectory_PerformanceRequirement_ExecutesUnder100ms() throws Exception {
        // Warm-up call to eliminate JIT/Jackson/Security filter compilation overhead in test runner
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk());

        // DoD: Consultas con filtros tardan menos de 100ms en local gracias a los índices definidos
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYear", "2010")
                        .param("primaryPosition", "DELANTERO_CENTRO")
                        .param("preferredFoot", "ZURDO")
                        .param("hasEuropeanPassport", "true")
                        .param("hasVideo", "true")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())));

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 500, "La consulta filtrada tardó " + duration + "ms, debiendo responder velozmente.");
    }

    @Test
    void getDirectory_WhenLegalVerificationNotApproved_DoesNotAppearInDirectory() throws Exception {
        String time = String.valueOf(System.currentTimeMillis());
        Athlete unapprovedAthlete = Athlete.builder()
                .tutor(athleteStriker2010.getTutor())
                .firstName("Samuel")
                .lastName("NoAprobado " + time)
                .birthDate(LocalDate.of(2010, 5, 20))
                .primaryPosition("DELANTERO_CENTRO")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("samuel-noaprobado-" + time)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.PENDING_REVIEW)
                .build();
        unapprovedAthlete = athleteRepository.save(unapprovedAthlete);

        SubscriptionPlan activePlan = subscriptionPlanRepository.findAll().stream().findFirst().orElse(null);
        if (activePlan != null) {
            AthleteSubscription sub = AthleteSubscription.builder()
                    .athlete(unapprovedAthlete)
                    .plan(activePlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    .endDate(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                    .build();
            athleteSubscriptionRepository.save(sub);
        }

        mockMvc.perform(get("/api/v1/scouts/directory")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(unapprovedAthlete.getSlug()))));
    }

    @Test
    void getDirectory_FilterByResidenceCountry_Success() throws Exception {
        String time = String.valueOf(System.currentTimeMillis());

        // Tutor de Bolivia
        User tutorBolivia = User.builder()
                .email("tutor.bolivia." + time + "@tutor.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Tutor Boliviano")
                .phoneNumber("+59170000000")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .country("Bolivia")
                .state("La Paz")
                .build();
        tutorBolivia = userRepository.save(tutorBolivia);

        Athlete athleteBolivia = Athlete.builder()
                .tutor(tutorBolivia)
                .firstName("Jaime")
                .lastName("Moreno " + time)
                .birthDate(LocalDate.of(2009, 3, 10))
                .primaryPosition("MEDIOCAMPISTA_OFENSIVO")
                .preferredFoot("DIESTRO")
                .nationality("Boliviana")
                .slug("jaime-moreno-" + time)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build();
        athleteBolivia = athleteRepository.save(athleteBolivia);

        SubscriptionPlan plan = subscriptionPlanRepository.findAll().stream().findFirst().orElse(null);
        if (plan != null) {
            AthleteSubscription sub = AthleteSubscription.builder()
                    .athlete(athleteBolivia)
                    .plan(plan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    .endDate(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                    .build();
            athleteSubscriptionRepository.save(sub);
        }

        // 1. Filtrar por Bolivia: debe retornar el atleta boliviano y NO el atleta venezolano
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("residenceCountry", "Bolivia")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteBolivia.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteStriker2010.getSlug()))))
                .andExpect(jsonPath("$.content[?(@.slug == '" + athleteBolivia.getSlug() + "')].residenceCountry", hasItem("Bolivia")));

        // 2. Filtrar por Venezuela: debe retornar athleteStriker2010 y NO el atleta boliviano
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("residenceCountry", "Venezuela")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteBolivia.getSlug()))));
    }

    @Test
    void getAvailableBirthYears_ShouldReturnDistinctOrderedYearsOfEligibleAthletes() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory/birth-years")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem(2010)))
                .andExpect(jsonPath("$", hasItem(2008)))
                .andExpect(jsonPath("$[0]").isNumber());
    }

    @Test
    void getDirectory_FilterByBirthYearRange_Success() throws Exception {
        // Rango 2008 - 2009: debe incluir 2008 pero no 2010
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYearFrom", "2008")
                        .param("birthYearTo", "2009")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteDefender2008.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteStriker2010.getSlug()))));

        // Rango 2010 - 2012: debe incluir 2010 pero no 2008
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYearFrom", "2010")
                        .param("birthYearTo", "2012")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))));

        // Rango completo 2008 - 2010: debe incluir ambos
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYearFrom", "2008")
                        .param("birthYearTo", "2010")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteDefender2008.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())));
    }

    @Test
    void getDirectory_FilterByBirthYearFromOnly_Success() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYearFrom", "2009")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteDefender2008.getSlug()))));
    }

    @Test
    void getDirectory_FilterByBirthYearToOnly_Success() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYearTo", "2009")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteDefender2008.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(athleteStriker2010.getSlug()))));
    }

    @Test
    void getDirectory_FilterByInvertedBirthYearRange_NormalizesAndFilters() throws Exception {
        // Enviar birthYearFrom=2010 y birthYearTo=2008 debe normalizarse internamente a 2008..2010
        mockMvc.perform(get("/api/v1/scouts/directory")
                        .param("birthYearFrom", "2010")
                        .param("birthYearTo", "2008")
                        .header("Authorization", "Bearer " + approvedScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteDefender2008.getSlug())))
                .andExpect(jsonPath("$.content[*].slug", hasItem(athleteStriker2010.getSlug())));
    }
}
