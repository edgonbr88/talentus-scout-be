package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteSubscription;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.domain.enums.VideoCategory;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.AthleteVideoRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.video.RequestUploadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class VideoControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private AthleteVideoRepository athleteVideoRepository;

    @Autowired
    private AthleteSubscriptionRepository athleteSubscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String tutorToken;
    private Athlete testAthlete;
    private User testTutor;
    private SubscriptionPlan proPlan;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String email = "tutor.video." + System.currentTimeMillis() + "@talentus.app";
        User tutor = new User(email, passwordEncoder.encode("Password123*"), "Tutor Video", "+584121234567", UserRole.TUTOR, AccountStatus.ACTIVE);
        testTutor = userRepository.save(tutor);
        tutorToken = jwtService.generateToken(testTutor);

        Athlete athlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Samuel")
                .lastName("Rojas")
                .birthDate(LocalDate.of(2010, 5, 12))
                .primaryPosition("Delantero Centro")
                .preferredFoot("Derecha")
                .slug("samuel-rojas-video-" + System.currentTimeMillis())
                .build();
        testAthlete = athleteRepository.save(athlete);

        proPlan = subscriptionPlanRepository.findById("plan-pro-mensual")
                .orElseGet(() -> subscriptionPlanRepository.save(
                        SubscriptionPlan.builder()
                                .id("plan-pro-mensual")
                                .name("Plan Pro Mensual")
                                .priceUsd(new BigDecimal("9.99"))
                                .durationDays(30)
                                .isActive(true)
                                .build()
                ));

        AthleteSubscription sub = AthleteSubscription.builder()
                .athlete(testAthlete)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minus(Duration.ofDays(1)))
                .endDate(Instant.now().plus(Duration.ofDays(29)))
                .build();
        athleteSubscriptionRepository.save(sub);
    }

    @Test
    void shouldRequestUploadTicketSuccessfully() throws Exception {
        RequestUploadRequest request = new RequestUploadRequest(
                testAthlete.getId(),
                "Jugadas vs Caracas FC",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.videoId").isNotEmpty())
                .andExpect(jsonPath("$.directUploadUrl").value(containsString("/api/v1/videos/mock-upload/")))
                .andExpect(jsonPath("$.providerVideoId").value(startsWith("mock-")))
                .andExpect(jsonPath("$.maxSizeBytes").value(524288000L));
    }

    @Test
    void shouldPerformMockUploadAndMarkVideoReady() throws Exception {
        // Primero creamos el registro en PENDING_UPLOAD
        AthleteVideo video = new AthleteVideo(
                testAthlete,
                "Gol de tiro libre",
                VideoCategory.GOLES,
                "mock-provider-" + UUID.randomUUID(),
                1
        );
        video = athleteVideoRepository.save(video);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tiro_libre.mp4",
                "video/mp4",
                "fake-video-content-stream-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/mock-upload/" + video.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(video.getId().toString()))
                .andExpect(jsonPath("$.status").value(VideoStatus.READY.name()))
                .andExpect(jsonPath("$.playbackUrl").value(containsString("/uploads/videos/")))
                .andExpect(jsonPath("$.thumbnailUrl").isNotEmpty());
    }

    @Test
    void shouldHandleWebhookPubliclyAndUpdateVideo() throws Exception {
        String providerId = "mock-webhook-" + UUID.randomUUID();
        AthleteVideo video = new AthleteVideo(
                testAthlete,
                "Asistencias Torneo Apertura",
                VideoCategory.ASISTENCIAS,
                providerId,
                2
        );
        video = athleteVideoRepository.save(video);

        String webhookPayload = String.format("""
                {
                    "providerVideoId": "%s",
                    "status": "ready",
                    "playbackUrl": "http://localhost:8080/uploads/videos/final.m3u8",
                    "thumbnailUrl": "http://localhost:8080/uploads/videos/thumb.jpg",
                    "durationSeconds": 85
                }
                """, providerId);

        mockMvc.perform(post("/api/v1/videos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        AthleteVideo updated = athleteVideoRepository.findById(video.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(VideoStatus.READY, updated.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(85, updated.getDurationSeconds());
    }

    @Test
    void shouldGetVideosByAthletePublicly() throws Exception {
        AthleteVideo video = new AthleteVideo(
                testAthlete,
                "Defensa y Bloqueos",
                VideoCategory.DEFENSA,
                "mock-provider-def",
                1
        );
        video.setStatus(VideoStatus.READY);
        video.setPlaybackUrl("http://localhost:8080/uploads/videos/def.mp4");
        athleteVideoRepository.save(video);

        mockMvc.perform(get("/api/v1/athletes/" + testAthlete.getId() + "/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].title").value("Defensa y Bloqueos"))
                .andExpect(jsonPath("$[0].category").value(VideoCategory.DEFENSA.name()))
                .andExpect(jsonPath("$[0].status").value(VideoStatus.READY.name()));
    }

    @Test
    void shouldRejectUploadTicketWhenAthleteBelongsToAnotherTutor() throws Exception {
        String otherEmail = "other.tutor." + System.currentTimeMillis() + "@talentus.app";
        User otherTutor = new User(otherEmail, passwordEncoder.encode("Password123*"), "Otro Tutor", "+584129998877", UserRole.TUTOR, AccountStatus.ACTIVE);
        otherTutor = userRepository.save(otherTutor);
        String otherTutorToken = jwtService.generateToken(otherTutor);

        RequestUploadRequest request = new RequestUploadRequest(
                testAthlete.getId(),
                "Intento no autorizado",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + otherTutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("No tienes autorización para cargar videos de este atleta.")));
    }

    @Test
    void shouldRejectUploadTicketWhenAthleteNotFound() throws Exception {
        RequestUploadRequest request = new RequestUploadRequest(
                UUID.randomUUID(),
                "Atleta Inexistente",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectUploadTicketWhenUserIsAdmin() throws Exception {
        String adminEmail = "admin.video." + System.currentTimeMillis() + "@talentus.app";
        User admin = new User(adminEmail, passwordEncoder.encode("Password123*"), "Admin Video", "+584120001122", UserRole.ADMIN, AccountStatus.ACTIVE);
        admin = userRepository.save(admin);
        String adminToken = jwtService.generateToken(admin);

        RequestUploadRequest request = new RequestUploadRequest(
                testAthlete.getId(),
                "Subida por Administrador",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldPerformMockUploadWithCustomThumbnail() throws Exception {
        AthleteVideo video = new AthleteVideo(
                testAthlete,
                "Gol de tiro libre con miniatura personalizada",
                VideoCategory.GOLES,
                "mock-provider-" + UUID.randomUUID(),
                3
        );
        video = athleteVideoRepository.save(video);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jugada_destacada.mp4",
                "video/mp4",
                "fake-video-content-stream-bytes-for-test".getBytes()
        );

        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnail",
                "thumb_custom.jpg",
                "image/jpeg",
                "fake-jpeg-image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/mock-upload/" + video.getId())
                        .file(file)
                        .file(thumbnail)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(video.getId().toString()))
                .andExpect(jsonPath("$.status").value(VideoStatus.READY.name()))
                .andExpect(jsonPath("$.playbackUrl").value(containsString("/uploads/videos/")))
                .andExpect(jsonPath("$.thumbnailUrl").value(containsString("_thumb.jpg")));
    }

    @Test
    void shouldServeDefaultThumbnailWith200Ok() throws Exception {
        mockMvc.perform(get("/uploads/videos/default-thumbnail.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/jpeg")));
    }

    @Test
    void shouldServeVideoWithByteRangesAnd206Status() throws Exception {
        AthleteVideo video = new AthleteVideo(
                testAthlete,
                "Control y Pase Largo",
                VideoCategory.HIGHLIGHTS,
                "mock-provider-" + UUID.randomUUID(),
                4
        );
        video = athleteVideoRepository.save(video);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "pase_largo.mp4",
                "video/mp4",
                "0123456789ABCDEF0123456789ABCDEF".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/mock-upload/" + video.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk());

        AthleteVideo savedVideo = athleteVideoRepository.findById(video.getId()).orElseThrow();
        String playbackUrl = savedVideo.getPlaybackUrl();
        String relativePath = playbackUrl.substring(playbackUrl.indexOf("/uploads/videos/"));

        mockMvc.perform(get(relativePath)
                        .header("Range", "bytes=0-10"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists("Content-Range"))
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    void shouldRejectUploadTicketWhenUserIsScout() throws Exception {
        String scoutEmail = "scout.video." + System.currentTimeMillis() + "@talentus.app";
        User scoutUser = new User(scoutEmail, passwordEncoder.encode("Password123*"), "Scout Usuario", "+584128887766", UserRole.SCOUT, AccountStatus.ACTIVE);
        scoutUser = userRepository.save(scoutUser);
        String scoutToken = jwtService.generateToken(scoutUser);

        RequestUploadRequest request = new RequestUploadRequest(
                testAthlete.getId(),
                "Intento de subida por scout",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + scoutToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUploadTicketWhenAthleteHasNoSubscription() throws Exception {
        Athlete unsubscribedAthlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Carlos")
                .lastName("SinPlan")
                .birthDate(LocalDate.of(2011, 3, 10))
                .primaryPosition("Mediocampista")
                .preferredFoot("Derecha")
                .slug("carlos-sin-plan-" + System.currentTimeMillis())
                .build();
        unsubscribedAthlete = athleteRepository.save(unsubscribedAthlete);

        RequestUploadRequest request = new RequestUploadRequest(
                unsubscribedAthlete.getId(),
                "Intento sin plan pro",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("El atleta no cuenta con una suscripción Pro activa para subir videos.")));
    }

    @Test
    void shouldRejectUploadTicketWhenAthleteSubscriptionIsExpired() throws Exception {
        Athlete expiredAthlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Luis")
                .lastName("Expirado")
                .birthDate(LocalDate.of(2010, 8, 20))
                .primaryPosition("Defensa Central")
                .preferredFoot("Derecha")
                .slug("luis-expirado-" + System.currentTimeMillis())
                .build();
        expiredAthlete = athleteRepository.save(expiredAthlete);

        AthleteSubscription expiredSub = AthleteSubscription.builder()
                .athlete(expiredAthlete)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minus(Duration.ofDays(60)))
                .endDate(Instant.now().minus(Duration.ofDays(30)))
                .build();
        athleteSubscriptionRepository.save(expiredSub);

        RequestUploadRequest request = new RequestUploadRequest(
                expiredAthlete.getId(),
                "Intento con suscripción expirada",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("El atleta no cuenta con una suscripción Pro activa para subir videos.")));
    }

    @Test
    void shouldRejectUploadTicketWhenAthleteSubscriptionIsCancelled() throws Exception {
        Athlete cancelledAthlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Mateo")
                .lastName("Cancelado")
                .birthDate(LocalDate.of(2012, 1, 15))
                .primaryPosition("Portero")
                .preferredFoot("Derecha")
                .slug("mateo-cancelado-" + System.currentTimeMillis())
                .build();
        cancelledAthlete = athleteRepository.save(cancelledAthlete);

        AthleteSubscription cancelledSub = AthleteSubscription.builder()
                .athlete(cancelledAthlete)
                .plan(proPlan)
                .status(SubscriptionStatus.CANCELLED)
                .startDate(Instant.now().minus(Duration.ofDays(5)))
                .endDate(Instant.now().plus(Duration.ofDays(25)))
                .build();
        athleteSubscriptionRepository.save(cancelledSub);

        RequestUploadRequest request = new RequestUploadRequest(
                cancelledAthlete.getId(),
                "Intento con suscripción cancelada",
                VideoCategory.HIGHLIGHTS
        );

        mockMvc.perform(post("/api/v1/videos/request-upload")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("El atleta no cuenta con una suscripción Pro activa para subir videos.")));
    }

    @Test
    void shouldRejectMockUploadWhenAthleteHasNoSubscription() throws Exception {
        Athlete unsubscribedAthlete = Athlete.builder()
                .tutor(testTutor)
                .firstName("Pedro")
                .lastName("SinSub")
                .birthDate(LocalDate.of(2011, 7, 5))
                .primaryPosition("Lateral")
                .preferredFoot("Derecha")
                .slug("pedro-sinsub-" + System.currentTimeMillis())
                .build();
        unsubscribedAthlete = athleteRepository.save(unsubscribedAthlete);

        AthleteVideo video = new AthleteVideo(
                unsubscribedAthlete,
                "Gol de Pedro",
                VideoCategory.GOLES,
                "mock-provider-unsub",
                1
        );
        video = athleteVideoRepository.save(video);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "gol.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/mock-upload/" + video.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("El atleta no cuenta con una suscripción Pro activa para subir videos.")));
    }
}

