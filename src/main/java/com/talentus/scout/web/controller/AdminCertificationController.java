package com.talentus.scout.web.controller;

import com.talentus.scout.service.CertificationService;
import com.talentus.scout.web.dto.AdminReviewCertificationRequest;
import com.talentus.scout.web.dto.CertificationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/certifications")
@RequiredArgsConstructor
@Slf4j
public class AdminCertificationController {

    private final CertificationService certificationService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CertificationResponse>> getPendingAdminCertifications() {
        List<CertificationResponse> pending = certificationService.getPendingAdminCertifications();
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CertificationResponse> reviewCertification(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminReviewCertificationRequest reviewRequest,
            Authentication authentication
    ) {
        CertificationResponse response = certificationService.adminReviewDocument(id, reviewRequest, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
