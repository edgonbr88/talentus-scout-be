package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.payment.BcvRateService;
import com.talentus.scout.service.payment.BinancePayService;
import com.talentus.scout.service.payment.PaymentConfigService;
import com.talentus.scout.service.payment.PaymentService;
import com.talentus.scout.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfigService paymentConfigService;
    private final BcvRateService bcvRateService;
    private final PaymentService paymentService;
    private final BinancePayService binancePayService;
    private final UserRepository userRepository;
    private final com.talentus.scout.service.payment.DiscountCodeService discountCodeService;

    @PostMapping("/coupons/validate")
    public ResponseEntity<ValidateCouponResponse> validateCoupon(
            @Valid @RequestBody ValidateCouponRequest request
    ) {
        ValidateCouponResponse response = discountCodeService.validateCoupon(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/coupons/redeem-free")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<PaymentTransactionResponse> redeemFreePromo(
            Authentication authentication,
            @Valid @RequestBody RedeemFreePromoRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        PaymentTransactionResponse response = discountCodeService.redeemFreePromo(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> getPaymentConfig() {
        return ResponseEntity.ok(paymentConfigService.getPaymentConfig());
    }

    @GetMapping("/bcv-rate")
    public ResponseEntity<BcvRateResponse> getBcvRate() {
        return ResponseEntity.ok(bcvRateService.getOfficialRate());
    }

    @PostMapping("/report-pago-movil")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<PaymentTransactionResponse> reportPagoMovil(
            Authentication authentication,
            @Valid @RequestBody ReportPagoMovilRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        PaymentTransactionResponse response = paymentService.reportPagoMovil(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/binance-pay/manual-report")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<PaymentTransactionResponse> reportBinanceManual(
            Authentication authentication,
            @Valid @RequestBody ReportBinanceManualPaymentRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        PaymentTransactionResponse response = paymentService.reportBinanceManual(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/binance-pay/create-order")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<CreateBinanceOrderResponse> createBinanceOrder(
            Authentication authentication,
            @Valid @RequestBody CreateBinanceOrderRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        CreateBinanceOrderResponse response = binancePayService.createOrder(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/binance-pay/webhook")
    public ResponseEntity<BinanceWebhookResponse> handleBinanceWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "BinancePay-Signature", required = false) String signature,
            @RequestHeader(value = "BinancePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "BinancePay-Nonce", required = false) String nonce
    ) {
        BinanceWebhookResponse response = binancePayService.handleWebhook(payload, signature, timestamp, nonce);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/binance-pay/mock-webhook")
    public ResponseEntity<BinanceWebhookResponse> handleMockWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "BinancePay-Signature", required = false) String signature,
            @RequestHeader(value = "BinancePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "BinancePay-Nonce", required = false) String nonce
    ) {
        String sig = signature != null ? signature : "mock-valid-sig";
        BinanceWebhookResponse response = binancePayService.handleWebhook(payload, sig, timestamp, nonce);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/binance-pay/mock-checkout/{tradeNo}")
    public ResponseEntity<String> getMockCheckout(@PathVariable String tradeNo) {
        return ResponseEntity.ok("<html><body style='background:#0B0E14;color:#F8FAFC;font-family:sans-serif;text-align:center;padding:50px;'>" +
                "<h1 style='color:#F0B90B;'>Binance Pay — Sandbox Simulation</h1>" +
                "<p>Order: <strong>" + tradeNo + "</strong></p>" +
                "<p>Payment simulated. Webhook can be sent to /api/v1/payments/binance-pay/mock-webhook.</p>" +
                "</body></html>");
    }

    @GetMapping("/athlete/{athleteId}/status")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<AthleteSubscriptionStatusResponse> getAthleteSubscriptionStatus(
            Authentication authentication,
            @PathVariable UUID athleteId
    ) {
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        boolean isAdmin = user != null && user.getRole() == com.talentus.scout.domain.enums.UserRole.ADMIN;
        return ResponseEntity.ok(paymentService.getAthleteSubscriptionStatus(athleteId, user != null ? user.getId() : null, isAdmin));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<List<PaymentTransactionResponse>> getPaymentHistory(
            Authentication authentication
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return ResponseEntity.ok(paymentService.getTutorPayments(user.getId()));
    }
}
