package com.talentus.scout.web.controller;

import com.talentus.scout.service.payment.AdminSubscriptionPlanService;
import com.talentus.scout.web.dto.CreateSubscriptionPlanRequest;
import com.talentus.scout.web.dto.SubscriptionPlanAdminDto;
import com.talentus.scout.web.dto.UpdateSubscriptionPlanRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/subscription-plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionPlanController {

    private final AdminSubscriptionPlanService adminSubscriptionPlanService;

    @GetMapping
    public ResponseEntity<List<SubscriptionPlanAdminDto>> getAllPlans() {
        return ResponseEntity.ok(adminSubscriptionPlanService.getAllPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanAdminDto> getPlanById(@PathVariable String id) {
        return ResponseEntity.ok(adminSubscriptionPlanService.getPlanById(id));
    }

    @PostMapping
    public ResponseEntity<SubscriptionPlanAdminDto> createPlan(
            @Valid @RequestBody CreateSubscriptionPlanRequest request
    ) {
        SubscriptionPlanAdminDto created = adminSubscriptionPlanService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionPlanAdminDto> updatePlan(
            @PathVariable String id,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(adminSubscriptionPlanService.updatePlan(id, request));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<SubscriptionPlanAdminDto> toggleStatus(@PathVariable String id) {
        return ResponseEntity.ok(adminSubscriptionPlanService.toggleStatus(id));
    }
}
