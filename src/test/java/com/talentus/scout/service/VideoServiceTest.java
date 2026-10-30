package com.talentus.scout.service;

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
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.video.VideoStreamProvider;
import com.talentus.scout.service.video.model.UploadTicket;
import com.talentus.scout.web.dto.video.RequestUploadRequest;
import com.talentus.scout.web.dto.video.UploadTicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoServiceTest {

    private AthleteRepository athleteRepository;
    private AthleteVideoRepository athleteVideoRepository;
    private AthleteSubscriptionRepository athleteSubscriptionRepository;
    private UserRepository userRepository;
    private VideoStreamProvider videoStreamProvider;
    private VideoService videoService;

    private User tutor;
    private Athlete athlete;
    private SubscriptionPlan proPlan;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        athleteRepository = mock(AthleteRepository.class);
        athleteVideoRepository = mock(AthleteVideoRepository.class);
        athleteSubscriptionRepository = mock(AthleteSubscriptionRepository.class);
        userRepository = mock(UserRepository.class);
        videoStreamProvider = mock(VideoStreamProvider.class);

        videoService = new VideoService(
                athleteRepository,
                athleteVideoRepository,
                athleteSubscriptionRepository,
                userRepository,
                videoStreamProvider,
                tempDir.toString(),
                "http://localhost:8080"
        );

        tutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@talentus.app")
                .fullName("Tutor Prueba")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();

        athlete = Athlete.builder()
                .id(UUID.randomUUID())
                .tutor(tutor)
                .firstName("Mateo")
                .lastName("Gómez")
                .birthDate(LocalDate.of(2010, 5, 10))
                .build();

        proPlan = SubscriptionPlan.builder()
                .id("plan-pro-mensual")
                .name("Plan Pro")
                .priceUsd(new BigDecimal("9.99"))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    @Test
    void requestUpload_WithActiveSubscription_Succeeds() {
        RequestUploadRequest request = new RequestUploadRequest(
                athlete.getId(),
                "Mejores Jugadas",
                VideoCategory.HIGHLIGHTS
        );

        AthleteSubscription activeSub = AthleteSubscription.builder()
                .athlete(athlete)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minus(Duration.ofDays(2)))
                .endDate(Instant.now().plus(Duration.ofDays(28)))
                .build();

        when(athleteRepository.findById(athlete.getId())).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));
        when(athleteVideoRepository.countByAthleteId(athlete.getId())).thenReturn(0L);

        AthleteVideo savedVideo = AthleteVideo.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .title("Mejores Jugadas")
                .category(VideoCategory.HIGHLIGHTS)
                .providerVideoId("pending")
                .sortOrder(1)
                .status(VideoStatus.PENDING_UPLOAD)
                .build();
        when(athleteVideoRepository.save(any(AthleteVideo.class))).thenReturn(savedVideo);

        UploadTicket ticket = new UploadTicket(
                savedVideo.getId(),
                "http://localhost:8080/api/v1/videos/mock-upload/" + savedVideo.getId(),
                "mock-provider-123",
                524288000L
        );
        when(videoStreamProvider.requestUploadTicket(any(), any(), any())).thenReturn(ticket);

        UploadTicketResponse response = videoService.requestUpload(request, tutor.getEmail());

        assertNotNull(response);
        assertEquals(savedVideo.getId(), response.videoId());
        assertEquals("mock-provider-123", response.providerVideoId());
    }

    @Test
    void requestUpload_WithoutSubscription_ThrowsAccessDenied() {
        RequestUploadRequest request = new RequestUploadRequest(
                athlete.getId(),
                "Jugadas sin plan",
                VideoCategory.HIGHLIGHTS
        );

        when(athleteRepository.findById(athlete.getId())).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                videoService.requestUpload(request, tutor.getEmail())
        );

        assertTrue(ex.getMessage().contains("El atleta no cuenta con una suscripción Pro activa para subir videos."));
        verify(athleteVideoRepository, never()).save(any());
    }

    @Test
    void requestUpload_WithExpiredSubscription_ThrowsAccessDenied() {
        RequestUploadRequest request = new RequestUploadRequest(
                athlete.getId(),
                "Jugadas plan vencido",
                VideoCategory.HIGHLIGHTS
        );

        AthleteSubscription expiredSub = AthleteSubscription.builder()
                .athlete(athlete)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minus(Duration.ofDays(60)))
                .endDate(Instant.now().minus(Duration.ofDays(1)))
                .build();

        when(athleteRepository.findById(athlete.getId())).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(expiredSub));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                videoService.requestUpload(request, tutor.getEmail())
        );

        assertTrue(ex.getMessage().contains("El atleta no cuenta con una suscripción Pro activa para subir videos."));
        verify(athleteVideoRepository, never()).save(any());
    }

    @Test
    void requestUpload_WhenUserNotTutorOfAthlete_ThrowsAccessDenied() {
        User otherTutor = User.builder()
                .id(UUID.randomUUID())
                .email("other@talentus.app")
                .role(UserRole.TUTOR)
                .build();

        RequestUploadRequest request = new RequestUploadRequest(
                athlete.getId(),
                "Intento no autorizado",
                VideoCategory.HIGHLIGHTS
        );

        when(athleteRepository.findById(athlete.getId())).thenReturn(Optional.of(athlete));
        when(userRepository.findByEmail(otherTutor.getEmail())).thenReturn(Optional.of(otherTutor));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                videoService.requestUpload(request, otherTutor.getEmail())
        );

        assertTrue(ex.getMessage().contains("No tienes autorización para cargar videos de este atleta"));
    }

    @Test
    void processMockUpload_WithoutActiveSubscription_ThrowsAccessDenied() {
        UUID videoId = UUID.randomUUID();
        AthleteVideo video = AthleteVideo.builder()
                .id(videoId)
                .athlete(athlete)
                .title("Gol")
                .status(VideoStatus.PENDING_UPLOAD)
                .build();

        when(athleteVideoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", "bytes".getBytes());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                videoService.processMockUpload(videoId, file)
        );

        assertTrue(ex.getMessage().contains("El atleta no cuenta con una suscripción Pro activa para subir videos."));
    }
}
