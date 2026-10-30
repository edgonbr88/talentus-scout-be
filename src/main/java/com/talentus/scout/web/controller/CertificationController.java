package com.talentus.scout.web.controller;

import com.talentus.scout.service.CertificationService;
import com.talentus.scout.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certifications")
@RequiredArgsConstructor
@Slf4j
public class CertificationController {

    private final CertificationService certificationService;

    @PostMapping("/request-document")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<CertificationResponse> requestCertificationWithDocument(
            @Valid @RequestBody CreateDocumentCertificationRequest request,
            Authentication authentication
    ) {
        CertificationResponse response = certificationService.requestCertificationWithDocument(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/request-token")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<CertificationResponse> requestCertificationWithToken(
            @Valid @RequestBody CreateTokenCertificationRequest request,
            Authentication authentication
    ) {
        CertificationResponse response = certificationService.requestCertificationWithToken(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<List<CertificationResponse>> getAthleteCertifications(@PathVariable("athleteId") UUID athleteId) {
        List<CertificationResponse> response = certificationService.getAthleteCertifications(athleteId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/token/{token}")
    public ResponseEntity<PublicTokenCertificationResponse> getPublicCertificationByToken(@PathVariable("token") String token) {
        PublicTokenCertificationResponse response = certificationService.getPublicCertificationByToken(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/{token}/submit")
    public ResponseEntity<CertificationResponse> submitTokenCertification(
            @PathVariable("token") String token,
            @Valid @RequestBody SubmitTokenCertificationRequest submitRequest,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        CertificationResponse response = certificationService.submitTokenCertification(token, submitRequest, clientIp);
        return ResponseEntity.ok(response);
    }
}
