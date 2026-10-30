package com.talentus.scout.web.controller;

import com.talentus.scout.service.ScoutContactService;
import com.talentus.scout.service.ScoutDirectoryService;
import com.talentus.scout.web.dto.AthleteFilterCriteria;
import com.talentus.scout.web.dto.ContactClickRequest;
import com.talentus.scout.web.dto.ContactClickResponse;
import com.talentus.scout.web.dto.ScoutDirectoryAthleteItem;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scouts")
@RequiredArgsConstructor
public class ScoutDirectoryController {

    private final ScoutDirectoryService scoutDirectoryService;
    private final ScoutContactService scoutContactService;

    @GetMapping("/directory/birth-years")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<List<Integer>> getAvailableBirthYears() {
        List<Integer> years = scoutDirectoryService.getAvailableBirthYears();
        return ResponseEntity.ok(years);
    }

    @GetMapping("/directory")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<Page<ScoutDirectoryAthleteItem>> getScoutDirectory(
            @RequestParam(required = false) Integer birthYear,
            @RequestParam(required = false) Integer birthYearFrom,
            @RequestParam(required = false) Integer birthYearTo,
            @RequestParam(required = false) String primaryPosition,
            @RequestParam(required = false) String preferredFoot,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) Boolean hasEuropeanPassport,
            @RequestParam(required = false) Boolean hasVideo,
            @RequestParam(required = false) String residenceCountry,
            @PageableDefault(page = 0, size = 12, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AthleteFilterCriteria criteria = AthleteFilterCriteria.builder()
                .birthYear(birthYear)
                .birthYearFrom(birthYearFrom)
                .birthYearTo(birthYearTo)
                .primaryPosition(primaryPosition)
                .preferredFoot(preferredFoot)
                .clubId(clubId)
                .hasEuropeanPassport(hasEuropeanPassport)
                .hasVideo(hasVideo)
                .residenceCountry(residenceCountry)
                .build();

        Page<ScoutDirectoryAthleteItem> result = scoutDirectoryService.searchDirectory(criteria, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/contact-click")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<ContactClickResponse> contactClick(
            @Valid @RequestBody ContactClickRequest request,
            Authentication authentication
    ) {
        ContactClickResponse response = scoutContactService.registerContactClick(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/log-view")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN') and @scoutSecurity.isVerifiedScout(authentication)")
    public ResponseEntity<Void> logView(
            @RequestParam("athleteId") UUID athleteId,
            Authentication authentication
    ) {
        scoutContactService.recordProfileView(authentication.getName(), athleteId);
        return ResponseEntity.ok().build();
    }
}
