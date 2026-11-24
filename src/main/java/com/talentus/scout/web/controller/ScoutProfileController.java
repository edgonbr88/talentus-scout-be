package com.talentus.scout.web.controller;

import com.talentus.scout.service.ScoutProfileService;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkSettingsResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutPhotoResponse;
import com.talentus.scout.web.dto.scoutnetwork.UpdateScoutNetworkSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/scout/profile")
@RequiredArgsConstructor
public class ScoutProfileController {

    private final ScoutProfileService scoutProfileService;

    @GetMapping("/network-settings")
    @PreAuthorize("hasRole('SCOUT') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<ScoutNetworkSettingsResponse> getNetworkSettings(Authentication authentication) {
        return ResponseEntity.ok(scoutProfileService.getNetworkSettings(authentication.getName()));
    }

    @PutMapping("/network-settings")
    @PreAuthorize("hasRole('SCOUT') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<ScoutNetworkSettingsResponse> updateNetworkSettings(
            @Valid @RequestBody UpdateScoutNetworkSettingsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scoutProfileService.updateNetworkSettings(authentication.getName(), request));
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SCOUT') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<ScoutPhotoResponse> uploadPublicPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scoutProfileService.uploadPublicPhoto(authentication.getName(), file));
    }

    @DeleteMapping("/photo")
    @PreAuthorize("hasRole('SCOUT') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<Void> deletePublicPhoto(Authentication authentication) {
        scoutProfileService.deletePublicPhoto(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
