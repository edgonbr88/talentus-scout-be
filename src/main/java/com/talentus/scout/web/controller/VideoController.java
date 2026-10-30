package com.talentus.scout.web.controller;

import com.talentus.scout.service.VideoService;
import com.talentus.scout.web.dto.video.AthleteVideoResponse;
import com.talentus.scout.web.dto.video.RequestUploadRequest;
import com.talentus.scout.web.dto.video.UploadTicketResponse;
import com.talentus.scout.web.dto.video.WebhookResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/api/v1/videos/request-upload")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<UploadTicketResponse> requestUpload(
            @Valid @RequestBody RequestUploadRequest request,
            Authentication authentication
    ) {
        String currentUserEmail = authentication != null ? authentication.getName() : null;
        UploadTicketResponse response = videoService.requestUpload(request, currentUserEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/api/v1/videos/mock-upload/{videoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<AthleteVideoResponse> mockUpload(
            @PathVariable("videoId") UUID videoId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail
    ) {
        AthleteVideoResponse response = videoService.processMockUpload(videoId, file, thumbnail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/videos/webhook")
    public ResponseEntity<WebhookResponse> handleWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader Map<String, String> headers
    ) {
        WebhookResponse response = videoService.processWebhook(payload != null ? payload : "", headers);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/athletes/{athleteId}/videos")
    public ResponseEntity<List<AthleteVideoResponse>> getVideosByAthlete(
            @PathVariable("athleteId") UUID athleteId
    ) {
        List<AthleteVideoResponse> videos = videoService.getVideosByAthlete(athleteId);
        return ResponseEntity.ok(videos);
    }
}
