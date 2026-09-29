package com.talentus.scout.web.controller;

import com.talentus.scout.service.AthleteService;
import com.talentus.scout.web.dto.AthleteResponse;
import com.talentus.scout.web.dto.CreateAthleteRequest;
import com.talentus.scout.web.dto.UpdateAthleteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/athletes")
public class AthleteController {

    private final AthleteService athleteService;

    public AthleteController(AthleteService athleteService) {
        this.athleteService = athleteService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AthleteResponse> createAthlete(
            @Valid @RequestBody CreateAthleteRequest request,
            Authentication authentication
    ) {
        AthleteResponse response = athleteService.createAthlete(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-athletes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AthleteResponse>> getMyAthletes(Authentication authentication) {
        List<AthleteResponse> athletes = athleteService.getMyAthletes(authentication.getName());
        return ResponseEntity.ok(athletes);
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
}
