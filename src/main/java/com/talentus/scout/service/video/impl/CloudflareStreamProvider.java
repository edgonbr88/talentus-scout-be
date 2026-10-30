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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "talentus.video.provider", havingValue = "cloudflare")
public class CloudflareStreamProvider implements VideoStreamProvider {

    private static final Logger log = LoggerFactory.getLogger(CloudflareStreamProvider.class);

    private final String accountId;
    private final String apiToken;
    private final String webhookSecret;
    private final long maxSizeBytes;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CloudflareStreamProvider(
            @Value("${talentus.video.cloudflare.account-id:}") String accountId,
            @Value("${talentus.video.cloudflare.api-token:}") String apiToken,
            @Value("${talentus.video.cloudflare.webhook-secret:}") String webhookSecret,
            @Value("${talentus.video.max-size-bytes:524288000}") long maxSizeBytes,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.webhookSecret = webhookSecret;
        this.maxSizeBytes = maxSizeBytes;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public UploadTicket requestUploadTicket(UUID videoId, UUID athleteId, String title) {
        String url = String.format("https://api.cloudflare.com/client/v4/accounts/%s/stream/direct_upload", accountId);

        Map<String, Object> body = new HashMap<>();
        body.put("maxDurationSeconds", 600);
        Map<String, String> meta = new HashMap<>();
        meta.put("name", title);
        meta.put("athleteId", athleteId.toString());
        meta.put("videoId", videoId.toString());
        body.put("meta", meta);

        log.info("CloudflareStreamProvider: Solicitando direct upload a Cloudflare para videoId={}", videoId);

        try {
            String responseStr = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode result = root.path("result");
            String directUploadUrl = result.path("uploadURL").asText();
            String providerVideoId = result.path("uid").asText();

            if (directUploadUrl == null || directUploadUrl.isBlank()) {
                throw new IllegalStateException("Cloudflare no retornó uploadURL válida: " + responseStr);
            }

            return new UploadTicket(videoId, directUploadUrl, providerVideoId, maxSizeBytes);
        } catch (Exception e) {
            log.error("Error al solicitar direct upload ticket a Cloudflare Stream: {}", e.getMessage(), e);
            throw new RuntimeException("Error en comunicación con Cloudflare Stream: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoDetails parseWebhook(String payload, Map<String, String> headers) {
        log.info("CloudflareStreamProvider: Procesando webhook de Cloudflare Stream");

        if (webhookSecret != null && !webhookSecret.isBlank()) {
            String signatureHeader = headers.getOrDefault("webhook-signature", headers.get("Webhook-Signature"));
            validateSignature(payload, signatureHeader);
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String providerVideoId = root.path("uid").asText();
            String state = root.path("status").path("state").asText("ready");

            VideoStatus status = "ready".equalsIgnoreCase(state) ? VideoStatus.READY :
                    ("erroneous".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state) ? VideoStatus.FAILED : VideoStatus.PROCESSING);

            String playbackUrl = root.path("playback").path("hls").asText(
                    String.format("https://videodelivery.net/%s/manifest/video.m3u8", providerVideoId)
            );
            String thumbnailUrl = root.path("thumbnail").asText(
                    String.format("https://videodelivery.net/%s/thumbnails/thumbnail.jpg", providerVideoId)
            );
            int durationSeconds = root.path("duration").asInt(0);

            return new VideoDetails(providerVideoId, status, playbackUrl, thumbnailUrl, durationSeconds);
        } catch (Exception e) {
            log.error("Error al parsear el payload del webhook de Cloudflare: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Payload de webhook inválido: " + e.getMessage(), e);
        }
    }

    private void validateSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new SecurityException("Falta encabezado Webhook-Signature de Cloudflare");
        }

        try {
            String time = null;
            String expectedSig = null;

            String[] parts = signatureHeader.split(",");
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    if ("time".equalsIgnoreCase(kv[0].trim())) {
                        time = kv[1].trim();
                    } else if ("sig1".equalsIgnoreCase(kv[0].trim())) {
                        expectedSig = kv[1].trim();
                    }
                }
            }

            String stringToSign = (time != null ? time + "." : "") + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : rawHmac) {
                hex.append(String.format("%02x", b));
            }
            String calculatedSig = hex.toString();

            if (expectedSig != null && !MessageDigest.isEqual(calculatedSig.getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("Firma del webhook de Cloudflare inválida");
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Error validando firma HMAC del webhook: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "cloudflare";
    }
}
