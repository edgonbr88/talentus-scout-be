package com.talentus.scout.web.controller;

import com.talentus.scout.service.ScoutNetworkService;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkContactRequest;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkContactResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkFiltersResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkItemResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scouts/network")
@RequiredArgsConstructor
public class ScoutNetworkController {

    private final ScoutNetworkService scoutNetworkService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<Page<ScoutNetworkItemResponse>> searchNetwork(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) String organizationName,
            @RequestParam(required = false) String country,
            @RequestParam(required = false, defaultValue = "false") boolean sameClubOnly,
            @RequestParam(required = false, defaultValue = "false") boolean includeSelf,
            @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<ScoutNetworkItemResponse> result = scoutNetworkService.searchNetwork(
                authentication.getName(),
                query,
                clubId,
                organizationName,
                country,
                sameClubOnly,
                includeSelf,
                pageable
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<ScoutNetworkFiltersResponse> getNetworkFilters(Authentication authentication) {
        return ResponseEntity.ok(scoutNetworkService.getNetworkFilters(authentication.getName()));
    }

    @PostMapping("/{scoutProfileId}/contact")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<ScoutNetworkContactResponse> contactScout(
            @PathVariable UUID scoutProfileId,
            @Valid @RequestBody ScoutNetworkContactRequest request,
            Authentication authentication
    ) {
        ScoutNetworkContactResponse response = scoutNetworkService.contactScout(
                authentication.getName(),
                scoutProfileId,
                request
        );
        return ResponseEntity.ok(response);
    }
}
