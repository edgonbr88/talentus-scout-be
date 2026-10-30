package com.talentus.scout.web.controller;

import com.talentus.scout.service.payment.AdminDiscountCodeService;
import com.talentus.scout.web.dto.CreateDiscountCodeRequest;
import com.talentus.scout.web.dto.DiscountCodeAdminDto;
import com.talentus.scout.web.dto.UpdateDiscountCodeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/discount-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiscountCodeController {

    private final AdminDiscountCodeService adminDiscountCodeService;

    @GetMapping
    public ResponseEntity<Page<DiscountCodeAdminDto>> getAllCodes(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(adminDiscountCodeService.getAllCodes(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscountCodeAdminDto> getCodeById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminDiscountCodeService.getCodeById(id));
    }

    @PostMapping
    public ResponseEntity<DiscountCodeAdminDto> createCode(
            @Valid @RequestBody CreateDiscountCodeRequest request
    ) {
        DiscountCodeAdminDto created = adminDiscountCodeService.createCode(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscountCodeAdminDto> updateCode(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiscountCodeRequest request
    ) {
        return ResponseEntity.ok(adminDiscountCodeService.updateCode(id, request));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<DiscountCodeAdminDto> toggleStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(adminDiscountCodeService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCode(@PathVariable UUID id) {
        adminDiscountCodeService.deleteCode(id);
        return ResponseEntity.noContent().build();
    }
}
