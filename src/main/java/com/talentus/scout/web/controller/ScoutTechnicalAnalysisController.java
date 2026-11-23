package com.talentus.scout.web.controller;

import com.talentus.scout.service.ScoutTechnicalAnalysisService;
import com.talentus.scout.web.dto.ScoutTechnicalAnalysisResponse;
import com.talentus.scout.web.dto.UpsertScoutTechnicalAnalysisRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scout/athletes/{athleteId}/technical-analyses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
public class ScoutTechnicalAnalysisController {

    private final ScoutTechnicalAnalysisService analysisService;

    @GetMapping
    public ResponseEntity<List<ScoutTechnicalAnalysisResponse>> listAnalyses(
            @PathVariable UUID athleteId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(analysisService.listByAthlete(athleteId, authentication));
    }

    @PostMapping
    public ResponseEntity<ScoutTechnicalAnalysisResponse> createAnalysis(
            @PathVariable UUID athleteId,
            @Valid @RequestBody UpsertScoutTechnicalAnalysisRequest request,
            Authentication authentication
    ) {
        ScoutTechnicalAnalysisResponse created = analysisService.createAnalysis(athleteId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{analysisId}")
    public ResponseEntity<ScoutTechnicalAnalysisResponse> updateAnalysis(
            @PathVariable UUID athleteId,
            @PathVariable UUID analysisId,
            @Valid @RequestBody UpsertScoutTechnicalAnalysisRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(analysisService.updateAnalysis(athleteId, analysisId, request, authentication));
    }

    @DeleteMapping("/{analysisId}")
    public ResponseEntity<Void> deleteAnalysis(
            @PathVariable UUID athleteId,
            @PathVariable UUID analysisId,
            Authentication authentication
    ) {
        analysisService.deleteAnalysis(athleteId, analysisId, authentication);
        return ResponseEntity.noContent().build();
    }
}
