package com.talentus.scout.web.controller;

import com.talentus.scout.service.AdminClubService;
import com.talentus.scout.web.dto.admin.club.AdminClubResponse;
import com.talentus.scout.web.dto.admin.club.ClubStatsResponse;
import com.talentus.scout.web.dto.admin.club.CreateClubRequest;
import com.talentus.scout.web.dto.admin.club.ToggleVerificationRequest;
import com.talentus.scout.web.dto.admin.club.UpdateClubRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/clubs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClubController {

    private final AdminClubService adminClubService;

    public AdminClubController(AdminClubService adminClubService) {
        this.adminClubService = adminClubService;
    }

    @GetMapping
    public ResponseEntity<Page<AdminClubResponse>> getClubs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(adminClubService.getClubs(search, status, pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<ClubStatsResponse> getStats() {
        return ResponseEntity.ok(adminClubService.getStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminClubResponse> getClubDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminClubService.getClubDetail(id));
    }

    @PostMapping
    public ResponseEntity<AdminClubResponse> createClub(@Valid @RequestBody CreateClubRequest request) {
        AdminClubResponse created = adminClubService.createClub(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminClubResponse> updateClub(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClubRequest request
    ) {
        return ResponseEntity.ok(adminClubService.updateClub(id, request));
    }

    @PatchMapping("/{id}/verify")
    public ResponseEntity<AdminClubResponse> toggleVerification(
            @PathVariable UUID id,
            @RequestBody(required = false) ToggleVerificationRequest request
    ) {
        Boolean targetVerified = (request != null) ? request.getVerifiedOfficial() : null;
        return ResponseEntity.ok(adminClubService.toggleVerification(id, targetVerified));
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadLogo(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        String logoUrl = adminClubService.uploadLogo(id, file);
        return ResponseEntity.ok(Map.of("logoUrl", logoUrl));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClub(@PathVariable UUID id) {
        adminClubService.deleteClub(id);
        return ResponseEntity.noContent().build();
    }
}
