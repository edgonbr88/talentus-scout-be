package com.talentus.scout.web.controller;

import com.talentus.scout.service.LegalVerificationService;
import com.talentus.scout.web.dto.legal.LegalVerificationPendingItemResponse;
import com.talentus.scout.web.dto.legal.LegalVerificationReviewResponse;
import com.talentus.scout.web.dto.legal.ReviewLegalVerificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/legal-verifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminLegalVerificationController {

    private final LegalVerificationService legalVerificationService;

    @GetMapping("/pending")
    public ResponseEntity<Page<LegalVerificationPendingItemResponse>> getPendingVerifications(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<LegalVerificationPendingItemResponse> response = legalVerificationService.getPendingVerifications(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getVerificationMetrics() {
        Map<String, Object> metrics = legalVerificationService.getVerificationMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/{athleteId}")
    public ResponseEntity<LegalVerificationPendingItemResponse> getVerificationDetail(
            @PathVariable UUID athleteId
    ) {
        LegalVerificationPendingItemResponse response = legalVerificationService.getVerificationDetail(athleteId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{athleteId}/review")
    public ResponseEntity<LegalVerificationReviewResponse> reviewVerification(
            @PathVariable UUID athleteId,
            @Valid @RequestBody ReviewLegalVerificationRequest request,
            Authentication authentication
    ) {
        String adminEmail = authentication.getName();
        LegalVerificationReviewResponse response = legalVerificationService.reviewVerification(athleteId, request, adminEmail);
        return ResponseEntity.ok(response);
    }
}
