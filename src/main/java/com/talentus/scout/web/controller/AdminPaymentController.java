package com.talentus.scout.web.controller;

import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.payment.AdminPaymentService;
import com.talentus.scout.web.dto.AdminPaymentSummaryResponse;
import com.talentus.scout.web.dto.AdminReviewPaymentRequest;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;
    private final UserRepository userRepository;

    @GetMapping("/summary")
    public ResponseEntity<AdminPaymentSummaryResponse> getMetricsSummary() {
        return ResponseEntity.ok(adminPaymentService.getMetricsSummary());
    }

    @GetMapping("/history")
    public ResponseEntity<Page<PaymentTransactionResponse>> getPaymentHistory(
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(adminPaymentService.getPaymentHistory(method, status, pageable));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PaymentTransactionResponse>> getPendingPayments() {
        return ResponseEntity.ok(adminPaymentService.getPendingPayments());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentTransactionResponse> approvePayment(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        User admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario administrador no encontrado."));
        return ResponseEntity.ok(adminPaymentService.approvePayment(id, admin.getId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PaymentTransactionResponse> rejectPayment(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestBody(required = false) AdminReviewPaymentRequest request
    ) {
        User admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario administrador no encontrado."));
        String reason = request != null ? request.getRejectionReason() : null;
        return ResponseEntity.ok(adminPaymentService.rejectPayment(id, admin.getId(), reason));
    }
}

