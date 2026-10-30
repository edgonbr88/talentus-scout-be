package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.domain.enums.VideoCategory;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteVideoRepository;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.UserRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AthletePublicControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private AthleteVideoRepository athleteVideoRepository;

    @Autowired
    private AthleteBadgeRepository athleteBadgeRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User testTutor;
    private ClubOrganization testClub;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String email = "tutor.public." + System.currentTimeMillis() + "@talentus.app";
        testTutor = new User(email, passwordEncoder.encode("Password123*"), "Tutor Publico", "+584145556677", UserRole.TUTOR, AccountStatus.ACTIVE);
        testTutor = userRepository.save(testTutor);

        testClub = ClubOrganization.builder()
                .name("Deportivo Miranda Sub-16")
                .federationCode("FED-MIR-01")
                .city("Caracas")
                .state("Miranda")
                .country("Venezuela")
                .logoUrl("https://cdn.talentus.app/logos/miranda.png")
                .verifiedOfficial(true)
                .build();
        testClub = clubOrganizationRepository.save(testClub);
    }

    @Test
    void getPublicAthleteBySlug_WithValidPublicAthlete_Returns200AndCompleteBentoData() throws Exception {
        String slug = "gabriel-perez-" + System.currentTimeMillis();

        Athlete athlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Gabriel")
                .lastName("Pérez")
                .birthDate(LocalDate.of(2010, 6, 15))
                .gender("MALE")
                .primaryPosition("Delantero Centro")
                .secondaryPosition("Extremo Derecho")
                .preferredFoot("Derecho")
                .nationality("Venezolana")
                .secondaryPassports("Español (UE)")
                .currentClub(testClub)
                .federationLicenseNumber("CCS-2024-9841")
                .heightCm(new BigDecimal("174.50"))
                .weightKg(new BigDecimal("63.00"))
                .profilePhotoUrl("https://cdn.talentus.app/photos/gabriel.jpg")
                .bio("Goleador juvenil con velocidad de punta.")
                .slug(slug)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.APPROVED)
                .build();
        athlete = athleteRepository.save(athlete);

        // Asociar video en estado READY
        AthleteVideo video = AthleteVideo.builder()
                .athlete(athlete)
                .title("Goles y Jugadas Destacadas")
                .category(VideoCategory.HIGHLIGHTS)
                .providerVideoId("cf-prov-" + System.currentTimeMillis())
                .playbackUrl("https://videodelivery.net/cf783b92019/manifest/video.m3u8")
                .thumbnailUrl("https://videodelivery.net/cf783b92019/thumbnails/thumbnail.jpg")
                .durationSeconds(85)
                .status(VideoStatus.READY)
                .sortOrder(1)
                .build();
        athleteVideoRepository.save(video);

        // Asociar insignia aprobada
        AthleteBadge badge = AthleteBadge.builder()
                .athlete(athlete)
                .badgeType(BadgeType.FEDERATION_LICENSE)
                .organization(testClub)
                .status(BadgeStatus.APPROVED)
                .verifiedAt(Instant.now())
                .build();
        athleteBadgeRepository.save(badge);

        // Llamada pública sin token
        mockMvc.perform(get("/api/v1/athletes/public/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.fullName").value("Gabriel Pérez"))
                .andExpect(jsonPath("$.primaryPosition").value("Delantero Centro"))
                .andExpect(jsonPath("$.preferredFoot").value("Derecho"))
                .andExpect(jsonPath("$.club.name").value("Deportivo Miranda Sub-16"))
                .andExpect(jsonPath("$.club.logoUrl").value("https://cdn.talentus.app/logos/miranda.png"))
                .andExpect(jsonPath("$.metrics.heightCm").value(174.50))
                .andExpect(jsonPath("$.metrics.weightKg").value(63.00))
                .andExpect(jsonPath("$.badges").isArray())
                .andExpect(jsonPath("$.badges[0].verified").value(true))
                .andExpect(jsonPath("$.videos").isArray())
                .andExpect(jsonPath("$.videos[0].title").value("Goles y Jugadas Destacadas"))
                .andExpect(jsonPath("$.videos[0].playbackUrl").value("https://videodelivery.net/cf783b92019/manifest/video.m3u8"))
                // LOPNNA Art. 65: Verificar que no se expongan datos del tutor ni teléfonos
                .andExpect(jsonPath("$.tutorId").doesNotExist())
                .andExpect(jsonPath("$.tutorEmail").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    void getPublicAthleteBySlug_WhenAthleteIsPrivate_Returns404NotFound() throws Exception {
        String slug = "atleta-privado-" + System.currentTimeMillis();

        Athlete athlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Carlos")
                .lastName("Privado")
                .birthDate(LocalDate.of(2011, 4, 10))
                .primaryPosition("PORTERO")
                .preferredFoot("DERECHO")
                .slug(slug)
                .isPublic(false) // PRIVADO
                .build();
        athleteRepository.save(athlete);

        mockMvc.perform(get("/api/v1/athletes/public/" + slug))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicAthleteBySlug_WhenSlugDoesNotExist_Returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/athletes/public/slug-inexistente-xyz-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicAthleteBySlug_WhenLegalVerificationPending_Returns404NotFound() throws Exception {
        String slug = "atleta-pendiente-legal-" + System.currentTimeMillis();

        Athlete athlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Luis")
                .lastName("Pendiente")
                .birthDate(LocalDate.of(2011, 4, 10))
                .primaryPosition("PORTERO")
                .preferredFoot("DERECHO")
                .slug(slug)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.PENDING_REVIEW)
                .build();
        athleteRepository.save(athlete);

        mockMvc.perform(get("/api/v1/athletes/public/" + slug))
                .andExpect(status().isNotFound());
    }
}
