package com.talentus.scout.service.video.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.service.video.VideoStreamProvider;
import com.talentus.scout.service.video.model.UploadTicket;
import com.talentus.scout.service.video.model.VideoDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "talentus.video.provider", havingValue = "mock", matchIfMissing = true)
public class MockLocalVideoStreamProvider implements VideoStreamProvider {

    private static final Logger log = LoggerFactory.getLogger(MockLocalVideoStreamProvider.class);

    private final String baseUrl;
    private final long maxSizeBytes;
    private final ObjectMapper objectMapper;

    public MockLocalVideoStreamProvider(
            @Value("${talentus.video.mock.base-url:http://localhost:8080}") String baseUrl,
            @Value("${talentus.video.max-size-bytes:524288000}") long maxSizeBytes,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxSizeBytes = maxSizeBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    public UploadTicket requestUploadTicket(UUID videoId, UUID athleteId, String title) {
        String directUploadUrl = baseUrl + "/api/v1/videos/mock-upload/" + videoId;
        String providerVideoId = "mock-" + UUID.randomUUID();
        log.info("MockLocalVideoStreamProvider: Generando ticket para videoId={}, directUploadUrl={}", videoId, directUploadUrl);
        return new UploadTicket(videoId, directUploadUrl, providerVideoId, maxSizeBytes);
    }

    @Override
    public VideoDetails parseWebhook(String payload, Map<String, String> headers) {
        log.info("MockLocalVideoStreamProvider: Recibido webhook simulado. Payload: {}", payload);
        String providerVideoId = "mock-unknown";
        VideoStatus status = VideoStatus.READY;
        String playbackUrl = baseUrl + "/uploads/videos/sample.mp4";
        String thumbnailUrl = baseUrl + "/uploads/videos/sample-thumb.jpg";
        int durationSeconds = 60;

        try {
            if (payload != null && !payload.isBlank()) {
                JsonNode root = objectMapper.readTree(payload);
                if (root.has("providerVideoId")) {
                    providerVideoId = root.get("providerVideoId").asText();
                } else if (root.has("uid")) {
                    providerVideoId = root.get("uid").asText();
                }
                if (root.has("status")) {
                    String statusText = root.get("status").asText();
                    if (statusText.equalsIgnoreCase("ready")) {
                        status = VideoStatus.READY;
                    } else if (statusText.equalsIgnoreCase("failed") || statusText.equalsIgnoreCase("error")) {
                        status = VideoStatus.FAILED;
                    }
                }
                if (root.has("playbackUrl")) {
                    playbackUrl = root.get("playbackUrl").asText();
                }
                if (root.has("thumbnailUrl")) {
                    thumbnailUrl = root.get("thumbnailUrl").asText();
                }
                if (root.has("durationSeconds")) {
                    durationSeconds = root.get("durationSeconds").asInt();
                }
            }
        } catch (Exception e) {
            log.warn("MockLocalVideoStreamProvider: No se pudo parsear el payload del webhook mock, usando valores por defecto: {}", e.getMessage());
        }

        return new VideoDetails(providerVideoId, status, playbackUrl, thumbnailUrl, durationSeconds);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }
}
