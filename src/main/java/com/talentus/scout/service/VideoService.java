package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.domain.enums.VideoCategory;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.AthleteVideoRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.video.VideoStreamProvider;
import com.talentus.scout.service.video.model.UploadTicket;
import com.talentus.scout.service.video.model.VideoDetails;
import com.talentus.scout.web.dto.video.AthleteVideoResponse;
import com.talentus.scout.web.dto.video.RequestUploadRequest;
import com.talentus.scout.web.dto.video.UploadTicketResponse;
import com.talentus.scout.web.dto.video.WebhookResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final AthleteRepository athleteRepository;
    private final AthleteVideoRepository athleteVideoRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final UserRepository userRepository;
    private final VideoStreamProvider videoStreamProvider;
    private final String uploadDir;
    private final String baseUrl;

    public VideoService(
            AthleteRepository athleteRepository,
            AthleteVideoRepository athleteVideoRepository,
            AthleteSubscriptionRepository athleteSubscriptionRepository,
            UserRepository userRepository,
            VideoStreamProvider videoStreamProvider,
            @Value("${talentus.video.upload-dir:uploads/videos}") String uploadDir,
            @Value("${talentus.video.mock.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.athleteRepository = athleteRepository;
        this.athleteVideoRepository = athleteVideoRepository;
        this.athleteSubscriptionRepository = athleteSubscriptionRepository;
        this.userRepository = userRepository;
        this.videoStreamProvider = videoStreamProvider;
        this.uploadDir = uploadDir;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @PostConstruct
    public void initDefaultThumbnail() {
        try {
            Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            ensureDefaultThumbnailExists(targetDir);
        } catch (Exception e) {
            log.warn("No se pudo inicializar la miniatura por defecto en {}: {}", uploadDir, e.getMessage());
        }
    }

    @Transactional
    public UploadTicketResponse requestUpload(RequestUploadRequest request, String currentUserEmail) {
        Athlete athlete = athleteRepository.findById(request.athleteId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con id: " + request.athleteId()));

        if (currentUserEmail == null || currentUserEmail.isBlank()) {
            throw new AccessDeniedException("Se requiere autenticación para solicitar la carga de videos.");
        }

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + currentUserEmail));

        if (currentUser.getRole() != UserRole.TUTOR || athlete.getTutor() == null || !athlete.getTutor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No tienes autorización para cargar videos de este atleta. Solo el tutor legal puede realizar esta acción.");
        }

        validateActiveSubscriptionForVideoUpload(athlete);

        int nextSortOrder = (int) athleteVideoRepository.countByAthleteId(athlete.getId()) + 1;

        AthleteVideo video = AthleteVideo.builder()
                .athlete(athlete)
                .title(request.title())
                .category(request.category() != null ? request.category() : VideoCategory.HIGHLIGHTS)
                .providerVideoId("pending")
                .sortOrder(nextSortOrder)
                .status(VideoStatus.PENDING_UPLOAD)
                .build();

        AthleteVideo saved = athleteVideoRepository.save(video);

        UploadTicket ticket = videoStreamProvider.requestUploadTicket(saved.getId(), athlete.getId(), request.title());
        saved.setProviderVideoId(ticket.getProviderVideoId());
        saved = athleteVideoRepository.save(saved);

        log.info("Video creado con ID {} para atleta {} con providerId {}", saved.getId(), athlete.getId(), ticket.getProviderVideoId());

        return new UploadTicketResponse(
                saved.getId(),
                ticket.getDirectUploadUrl(),
                ticket.getProviderVideoId(),
                ticket.getMaxSizeBytes()
        );
    }

    @Transactional
    public AthleteVideoResponse processMockUpload(UUID videoId, MultipartFile file) {
        return processMockUpload(videoId, file, null);
    }

    @Transactional
    public AthleteVideoResponse processMockUpload(UUID videoId, MultipartFile file, MultipartFile thumbnail) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de video es obligatorio y no puede estar vacío");
        }

        AthleteVideo video = athleteVideoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video no encontrado con id: " + videoId));

        if (video.getAthlete() != null) {
            validateActiveSubscriptionForVideoUpload(video.getAthlete());
        }

        try {
            Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalFilename = file.getOriginalFilename();
            String cleanFilename = originalFilename != null ? StringUtils.cleanPath(originalFilename) : "video.mp4";
            String storedFilename = videoId + "_" + cleanFilename;
            Path destinationFile = targetDir.resolve(storedFilename);

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archivo local guardado en {}", destinationFile);

            String playbackUrl = baseUrl + "/uploads/videos/" + storedFilename;
            String thumbnailUrl;

            if (thumbnail != null && !thumbnail.isEmpty()) {
                String thumbFilename = videoId + "_thumb.jpg";
                Path thumbDestination = targetDir.resolve(thumbFilename);
                Files.copy(thumbnail.getInputStream(), thumbDestination, StandardCopyOption.REPLACE_EXISTING);
                thumbnailUrl = baseUrl + "/uploads/videos/" + thumbFilename;
                log.info("Thumbnail personalizado guardado en {}", thumbDestination);
            } else {
                ensureDefaultThumbnailExists(targetDir);
                thumbnailUrl = baseUrl + "/uploads/videos/default-thumbnail.jpg";
            }

            video.setPlaybackUrl(playbackUrl);
            video.setThumbnailUrl(thumbnailUrl);
            video.setStatus(VideoStatus.READY);
            video.setDurationSeconds(60);

            AthleteVideo updated = athleteVideoRepository.save(video);
            return AthleteVideoResponse.fromEntity(updated);
        } catch (IOException e) {
            log.error("Error al almacenar el archivo de video local: {}", e.getMessage(), e);
            throw new RuntimeException("Error al almacenar el archivo de video: " + e.getMessage(), e);
        }
    }

    private void ensureDefaultThumbnailExists(Path targetDir) {
        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path defaultThumbPath = targetDir.resolve("default-thumbnail.jpg");
            if (!Files.exists(defaultThumbPath)) {
                int width = 640;
                int height = 360;
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Fondo oscuro Bento Pitch
                g.setColor(new Color(15, 23, 42)); // #0F172A
                g.fillRect(0, 0, width, height);

                // Líneas de cancha sutiles en verde césped Pitch
                g.setColor(new Color(16, 185, 129, 45)); // #10B981 semi-transparente
                g.drawRect(40, 30, width - 80, height - 60);
                g.drawLine(width / 2, 30, width / 2, height - 30);
                g.drawOval(width / 2 - 50, height / 2 - 50, 100, 100);

                // Círculo central de play
                g.setColor(new Color(16, 185, 129, 200));
                g.fillOval(width / 2 - 32, height / 2 - 32, 64, 64);
                g.setColor(Color.WHITE);
                int[] xPoints = {width / 2 - 8, width / 2 - 8, width / 2 + 14};
                int[] yPoints = {height / 2 - 14, height / 2 + 14, height / 2};
                g.fillPolygon(xPoints, yPoints, 3);

                // Tipografía Bento
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                g.setColor(new Color(248, 250, 252)); // #F8FAFC
                String title = "TALENTUS SCOUT";
                int titleWidth = g.getFontMetrics().stringWidth(title);
                g.drawString(title, (width - titleWidth) / 2, height / 2 + 65);

                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(148, 163, 184)); // #94A3B8
                String subtitle = "VIDEO DESTACADO";
                int subWidth = g.getFontMetrics().stringWidth(subtitle);
                g.drawString(subtitle, (width - subWidth) / 2, height / 2 + 88);

                g.dispose();
                ImageIO.write(image, "jpg", defaultThumbPath.toFile());
                log.info("Miniatura por defecto creada en {}", defaultThumbPath);
            }
        } catch (Exception e) {
            log.warn("Error al generar la miniatura por defecto: {}", e.getMessage());
        }
    }

    @Transactional
    public WebhookResponse processWebhook(String payload, Map<String, String> headers) {
        VideoDetails details = videoStreamProvider.parseWebhook(payload, headers);
        log.info("Detalles de webhook parseados: providerId={}, status={}", details.getProviderVideoId(), details.getStatus());

        return athleteVideoRepository.findByProviderVideoId(details.getProviderVideoId())
                .map(video -> {
                    if (details.getStatus() != null) {
                        video.setStatus(details.getStatus());
                    }
                    if (details.getPlaybackUrl() != null && !details.getPlaybackUrl().isBlank()) {
                        video.setPlaybackUrl(details.getPlaybackUrl());
                    }
                    if (details.getThumbnailUrl() != null && !details.getThumbnailUrl().isBlank()) {
                        video.setThumbnailUrl(details.getThumbnailUrl());
                    }
                    if (details.getDurationSeconds() != null && details.getDurationSeconds() > 0) {
                        video.setDurationSeconds(details.getDurationSeconds());
                    }
                    athleteVideoRepository.save(video);
                    log.info("Video {} actualizado exitosamente por webhook al estado {}", video.getId(), video.getStatus());
                    return new WebhookResponse("SUCCESS", "Video " + video.getId() + " actualizado a " + video.getStatus());
                })
                .orElseGet(() -> {
                    log.warn("No se encontró video para providerVideoId={}", details.getProviderVideoId());
                    return new WebhookResponse("IGNORED", "No se encontró video con providerId: " + details.getProviderVideoId());
                });
    }

    @Transactional(readOnly = true)
    public List<AthleteVideoResponse> getVideosByAthlete(UUID athleteId) {
        if (!athleteRepository.existsById(athleteId)) {
            throw new ResourceNotFoundException("Atleta no encontrado con id: " + athleteId);
        }

        return athleteVideoRepository.findByAthleteIdOrderBySortOrderAscCreatedAtDesc(athleteId)
                .stream()
                .map(AthleteVideoResponse::fromEntity)
                .toList();
    }

    private void validateActiveSubscriptionForVideoUpload(Athlete athlete) {
        Instant now = Instant.now();
        boolean hasActiveSubscription = athleteSubscriptionRepository
                .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE)
                .filter(sub -> sub.getEndDate() != null && sub.getEndDate().isAfter(now))
                .isPresent();

        if (!hasActiveSubscription) {
            log.warn("Intento de subida de video denegado para atleta {} (ID: {}). No cuenta con suscripción Pro activa.",
                    athlete.getFirstName() + " " + athlete.getLastName(), athlete.getId());
            throw new AccessDeniedException("El atleta no cuenta con una suscripción Pro activa para subir videos.");
        }
    }
}
