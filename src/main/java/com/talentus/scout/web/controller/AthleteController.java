package com.talentus.scout.web.controller;

import com.talentus.scout.service.AthleteService;
import com.talentus.scout.service.ScoutContactService;
import com.talentus.scout.service.pdf.AthleteCvPdfService;
import com.talentus.scout.web.dto.AthleteResponse;
import com.talentus.scout.web.dto.CreateAthleteRequest;
import com.talentus.scout.web.dto.TutorRadarMetricsResponse;
import com.talentus.scout.web.dto.UpdateAthleteRequest;
import com.talentus.scout.web.dto.publicprofile.PublicAthleteResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/athletes")
public class AthleteController {

    private final AthleteService athleteService;
    private final AthleteCvPdfService athleteCvPdfService;
    private final ScoutContactService scoutContactService;

    public AthleteController(AthleteService athleteService,
                             AthleteCvPdfService athleteCvPdfService,
                             ScoutContactService scoutContactService) {
        this.athleteService = athleteService;
        this.athleteCvPdfService = athleteCvPdfService;
        this.scoutContactService = scoutContactService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<AthleteResponse> createAthlete(
            @Valid @RequestBody CreateAthleteRequest request,
            Authentication authentication
    ) {
        AthleteResponse response = athleteService.createAthlete(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-athletes")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<List<AthleteResponse>> getMyAthletes(Authentication authentication) {
        List<AthleteResponse> athletes = athleteService.getMyAthletes(authentication.getName());
        return ResponseEntity.ok(athletes);
    }

    @GetMapping("/radar")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<TutorRadarMetricsResponse> getTutorRadar(Authentication authentication) {
        TutorRadarMetricsResponse metrics = scoutContactService.getTutorRadarMetrics(authentication.getName());
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@athleteSecurity.isOwner(#id, authentication)")
    public ResponseEntity<AthleteResponse> getAthleteById(@PathVariable("id") UUID id) {
        AthleteResponse athlete = athleteService.getAthleteById(id);
        return ResponseEntity.ok(athlete);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@athleteSecurity.isOwner(#id, authentication)")
    public ResponseEntity<AthleteResponse> updateAthlete(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateAthleteRequest request
    ) {
        AthleteResponse updated = athleteService.updateAthlete(id, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(value = "/{id}/identity-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@athleteSecurity.isOwner(#id, authentication)")
    public ResponseEntity<AthleteResponse> uploadIdentityDocument(
            @PathVariable("id") UUID id,
            @Valid @ModelAttribute com.talentus.scout.web.dto.UploadAthleteIdentityDocumentRequest request
    ) {
        AthleteResponse updated = athleteService.uploadIdentityDocument(id, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/public/{slug}")
    public ResponseEntity<PublicAthleteResponse> getPublicAthleteBySlug(@PathVariable("slug") String slug) {
        PublicAthleteResponse response = athleteService.getPublicAthleteBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/cv-pdf")
    @PreAuthorize("@athleteSecurity.canDownloadCv(#id, authentication)")
    public ResponseEntity<byte[]> getAthleteCvPdf(@PathVariable("id") UUID id) {
        byte[] pdfBytes = athleteCvPdfService.generateAthleteCvPdf(id);
        AthleteResponse athlete = athleteService.getAthleteById(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("CV_Deportivo_" + athlete.getSlug() + ".pdf")
                .build());
        headers.setCacheControl(CacheControl.noCache().mustRevalidate());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@athleteSecurity.isOwner(#id, authentication)")
    public ResponseEntity<AthleteResponse> uploadProfilePhoto(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        AthleteResponse updated = athleteService.uploadProfilePhoto(id, file);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/photo")
    @PreAuthorize("@athleteSecurity.isOwner(#id, authentication)")
    public ResponseEntity<AthleteResponse> deleteProfilePhoto(@PathVariable("id") UUID id) {
        AthleteResponse updated = athleteService.deleteProfilePhoto(id);
        return ResponseEntity.ok(updated);
    }
}
