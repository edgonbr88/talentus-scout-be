package com.talentus.scout.web.controller;

import com.talentus.scout.service.AdminScoutService;
import com.talentus.scout.web.dto.PendingScoutResponse;
import com.talentus.scout.web.dto.ReviewScoutRequest;
import com.talentus.scout.web.dto.ScoutReviewResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/scouts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScoutController {

    private final AdminScoutService adminScoutService;

    public AdminScoutController(AdminScoutService adminScoutService) {
        this.adminScoutService = adminScoutService;
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<PendingScoutResponse>> getPendingScouts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PendingScoutResponse> response = adminScoutService.getPendingScouts(pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{scoutProfileId}/review")
    public ResponseEntity<ScoutReviewResponse> reviewScout(
            @PathVariable UUID scoutProfileId,
            @Valid @RequestBody ReviewScoutRequest request,
            Authentication authentication
    ) {
        String adminEmail = authentication.getName();
        ScoutReviewResponse response = adminScoutService.reviewScout(scoutProfileId, request, adminEmail);
        return ResponseEntity.ok(response);
    }
}
