package com.talentus.scout.service.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.service.video.impl.CloudflareStreamProvider;
import com.talentus.scout.service.video.model.VideoDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CloudflareStreamProviderTest {

    private CloudflareStreamProvider provider;
    private ObjectMapper objectMapper;
    private final String secret = "test_webhook_secret_key_123456";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new CloudflareStreamProvider(
                "mock-account-id",
                "mock-api-token",
                secret,
                524288000L,
                RestClient.builder(),
                objectMapper
        );
    }

    @Test
    void shouldParseCloudflareWebhookSuccessfullyWithValidSignature() throws Exception {
        String payload = """
                {
                    "uid": "cf-uid-9988",
                    "status": { "state": "ready" },
                    "playback": { "hls": "https://videodelivery.net/cf-uid-9988/manifest/video.m3u8" },
                    "thumbnail": "https://videodelivery.net/cf-uid-9988/thumbnails/thumbnail.jpg",
                    "duration": 95
                }
                """;

        String time = "1720000000";
        String stringToSign = time + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) {
            hex.append(String.format("%02x", b));
        }

        String signatureHeader = "time=" + time + ",sig1=" + hex;
        Map<String, String> headers = Map.of("Webhook-Signature", signatureHeader);

        VideoDetails details = provider.parseWebhook(payload, headers);

        assertNotNull(details);
        assertEquals("cf-uid-9988", details.getProviderVideoId());
        assertEquals(VideoStatus.READY, details.getStatus());
        assertEquals("https://videodelivery.net/cf-uid-9988/manifest/video.m3u8", details.getPlaybackUrl());
        assertEquals(95, details.getDurationSeconds());
    }

    @Test
    void shouldRejectWebhookWithInvalidSignature() {
        String payload = "{\"uid\": \"cf-uid-111\"}";
        Map<String, String> headers = Map.of("Webhook-Signature", "time=1720000000,sig1=invalid_signature");

        assertThrows(SecurityException.class, () -> provider.parseWebhook(payload, headers));
    }

    @Test
    void shouldReturnCorrectProviderName() {
        assertEquals("cloudflare", provider.getProviderName());
    }
}
