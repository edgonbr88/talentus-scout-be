package com.talentus.scout.service.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.service.video.impl.MockLocalVideoStreamProvider;
import com.talentus.scout.service.video.model.UploadTicket;
import com.talentus.scout.service.video.model.VideoDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MockLocalVideoStreamProviderTest {

    private MockLocalVideoStreamProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new MockLocalVideoStreamProvider("http://localhost:8080", 524288000L, objectMapper);
    }

    @Test
    void shouldGenerateUploadTicketCorrectly() {
        UUID videoId = UUID.randomUUID();
        UUID athleteId = UUID.randomUUID();

        UploadTicket ticket = provider.requestUploadTicket(videoId, athleteId, "Gol de chilena");

        assertNotNull(ticket);
        assertEquals(videoId, ticket.getVideoId());
        assertEquals(524288000L, ticket.getMaxSizeBytes());
        assertTrue(ticket.getDirectUploadUrl().contains("/api/v1/videos/mock-upload/" + videoId));
        assertTrue(ticket.getProviderVideoId().startsWith("mock-"));
    }

    @Test
    void shouldParseWebhookPayloadCorrectly() {
        String payload = """
                {
                    "providerVideoId": "mock-12345",
                    "status": "ready",
                    "playbackUrl": "http://localhost:8080/uploads/videos/test.mp4",
                    "thumbnailUrl": "http://localhost:8080/uploads/videos/test.jpg",
                    "durationSeconds": 120
                }
                """;

        VideoDetails details = provider.parseWebhook(payload, Map.of());

        assertNotNull(details);
        assertEquals("mock-12345", details.getProviderVideoId());
        assertEquals(VideoStatus.READY, details.getStatus());
        assertEquals("http://localhost:8080/uploads/videos/test.mp4", details.getPlaybackUrl());
        assertEquals(120, details.getDurationSeconds());
    }

    @Test
    void shouldFallbackGracefullyOnEmptyPayload() {
        VideoDetails details = provider.parseWebhook("", Map.of());

        assertNotNull(details);
        assertEquals("mock-unknown", details.getProviderVideoId());
        assertEquals(VideoStatus.READY, details.getStatus());
    }

    @Test
    void shouldReturnCorrectProviderName() {
        assertEquals("mock", provider.getProviderName());
    }
}
