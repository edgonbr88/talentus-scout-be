package com.talentus.scout.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.VexPayProperties;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.VexPayApiException;
import com.talentus.scout.repository.*;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VexPayServiceTest {

    private VexPayProperties properties;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private PaymentTransactionRepository paymentTransactionRepository;
    private VexPayWebhookEventRepository webhookEventRepository;
    private AthleteSubscriptionRepository athleteSubscriptionRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private AthleteRepository athleteRepository;
    private UserRepository userRepository;
    private PaymentGatewaySettingRepository paymentGatewaySettingRepository;
    private DiscountCodeService discountCodeService;
    private DiscountCodeRepository discountCodeRepository;
    private DiscountCodeRedemptionRepository discountCodeRedemptionRepository;
    private EmailService emailService;

    private VexPayService vexPayService;

    private User testTutor;
    private Athlete testAthlete;
    private SubscriptionPlan testPlan;
    private PaymentGatewaySetting gatewaySetting;

    @BeforeEach
    void setUp() {
        properties = new VexPayProperties();
        properties.setApiUrl("https://api.vexpay.app");
        properties.setApiKey("vk_test_1234567890");
        properties.setWebhookSecret("whsec_super_secret_test");
        properties.setWebhookToleranceSeconds(300L);
        properties.setSuccessUrl("http://localhost/dashboard?payment=vexpay_return");
        properties.setCancelUrl("http://localhost/dashboard?payment=vexpay_cancelled");
        properties.setReconcileLookbackHours(48L);

        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        webhookEventRepository = mock(VexPayWebhookEventRepository.class);
        athleteSubscriptionRepository = mock(AthleteSubscriptionRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        athleteRepository = mock(AthleteRepository.class);
        userRepository = mock(UserRepository.class);
        paymentGatewaySettingRepository = mock(PaymentGatewaySettingRepository.class);
        discountCodeService = mock(DiscountCodeService.class);
        discountCodeRepository = mock(DiscountCodeRepository.class);
        discountCodeRedemptionRepository = mock(DiscountCodeRedemptionRepository.class);
        emailService = mock(EmailService.class);

        vexPayService = new VexPayService(
                properties,
                restTemplate,
                objectMapper,
                paymentTransactionRepository,
                webhookEventRepository,
                athleteSubscriptionRepository,
                subscriptionPlanRepository,
                athleteRepository,
                userRepository,
                paymentGatewaySettingRepository,
                discountCodeService,
                discountCodeRepository,
                discountCodeRedemptionRepository,
                emailService
        );

        testTutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@vexpay.test")
                .fullName("Carlos Representante")
                .role(UserRole.TUTOR)
                .build();

        testAthlete = Athlete.builder()
                .id(UUID.randomUUID())
                .firstName("Gabriel")
                .lastName("Rivas")
                .tutor(testTutor)
                .build();

        testPlan = SubscriptionPlan.builder()
                .id("PRO_MONTHLY")
                .name("Plan Pro Mensual")
                .priceUsd(new BigDecimal("10.00"))
                .durationDays(30)
                .isActive(true)
                .build();

        gatewaySetting = PaymentGatewaySetting.builder()
                .id(UUID.randomUUID())
                .vexpayEnabled(true)
                .build();

        when(paymentGatewaySettingRepository.findSingleton()).thenReturn(Optional.of(gatewaySetting));
    }

    @Test
    void getQuote_WithPlanAndCoupon_CallsVexPayAndReturnsQuote() {
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));
        when(discountCodeService.validateCoupon("PROMO20", testAthlete.getId(), "PRO_MONTHLY"))
                .thenReturn(ValidateCouponResponse.builder()
                        .valid(true)
                        .code("PROMO20")
                        .originalAmountUsd(new BigDecimal("10.00"))
                        .discountAmountUsd(new BigDecimal("2.00"))
                        .finalAmountUsd(new BigDecimal("8.00"))
                        .isFreePromo(false)
                        .build());

        String quoteJson = """
                {
                  "usdAmount": 8,
                  "bcvRate": 915.7476,
                  "rate": 915.7476,
                  "rateSource": "BCV",
                  "vesAmount": 7325.98,
                  "medianRate": 916.10,
                  "medianVesAmount": 7328.80,
                  "fetchedAt": "2026-11-17T20:00:00Z"
                }
                """;
        when(restTemplate.exchange(
                eq("https://api.vexpay.app/v1/quote?usdAmount=8.00"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(quoteJson));

        VexPayQuoteResponse response = vexPayService.getQuote(null, "PRO_MONTHLY", "PROMO20", testAthlete.getId());

        assertNotNull(response);
        assertEquals(0, new BigDecimal("8").compareTo(response.getUsdAmount()));
        assertEquals(0, new BigDecimal("7325.98").compareTo(response.getVesAmount()));
        assertEquals("BCV", response.getRateSource());
        assertEquals("PROMO20", response.getDiscountCode());
    }

    @Test
    void createCheckoutSession_Success_PersistsPendingGatewayAndOmitsMethods() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));

        UUID txId = UUID.randomUUID();
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(txId);
            }
            return tx;
        });

        String sessionJson = """
                {
                  "id": "cs_abc123",
                  "object": "checkout.session",
                  "status": "open",
                  "url": "https://pay.vexpay.app/pay/cs-abc123",
                  "amountUsd": "10.00",
                  "expiresAt": "2026-11-17T23:00:00Z",
                  "livemode": false
                }
                """;

        when(restTemplate.exchange(
                eq("https://api.vexpay.app/v1/checkout/sessions"),
                eq(HttpMethod.POST),
                argThat((HttpEntity<?> req) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) req.getBody();
                    return body != null
                            && !body.containsKey("methods")
                            && txId.toString().equals(body.get("reference"))
                            && "vk_test_1234567890".equals(req.getHeaders().getFirst("x-api-key"))
                            && ("vexpay-session-" + txId).equals(req.getHeaders().getFirst("Idempotency-Key"));
                }),
                eq(String.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(sessionJson));

        CreateVexPaySessionResponse resp = vexPayService.createCheckoutSession(
                testTutor.getId(),
                false,
                CreateVexPaySessionRequest.builder()
                        .athleteId(testAthlete.getId())
                        .planId("PRO_MONTHLY")
                        .build()
        );

        assertEquals("cs_abc123", resp.getSessionId());
        assertEquals("https://pay.vexpay.app/pay/cs-abc123", resp.getCheckoutUrl());
        assertEquals(txId, resp.getTransactionId());
        assertFalse(resp.isLivemode());
    }

    @Test
    void createCheckoutSession_GatewayDisabled_ThrowsIllegalState() {
        gatewaySetting.setVexpayEnabled(false);

        assertThrows(IllegalStateException.class, () -> vexPayService.createCheckoutSession(
                testTutor.getId(),
                false,
                CreateVexPaySessionRequest.builder()
                        .athleteId(testAthlete.getId())
                        .planId("PRO_MONTHLY")
                        .build()
        ));
    }

    @Test
    void createCheckoutSession_VexPay409And503_MapsToVexPayApiException() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });

        HttpClientErrorException conflictEx = HttpClientErrorException.create(
                HttpStatus.CONFLICT,
                "Conflict",
                HttpHeaders.EMPTY,
                "{\"error\":\"payout_account_required\",\"message\":\"Payout account required\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(conflictEx);

        VexPayApiException ex409 = assertThrows(VexPayApiException.class, () ->
                vexPayService.createCheckoutSession(
                        testTutor.getId(),
                        false,
                        CreateVexPaySessionRequest.builder()
                                .athleteId(testAthlete.getId())
                                .planId("PRO_MONTHLY")
                                .build()
                ));
        assertEquals(HttpStatus.CONFLICT, ex409.getStatus());
        assertEquals("payout_account_required", ex409.getErrorCode());

        HttpServerErrorException rateEx = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                HttpHeaders.EMPTY,
                "{\"error\":\"rate_unavailable\",\"message\":\"Market rate unavailable\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(rateEx);

        VexPayApiException ex503 = assertThrows(VexPayApiException.class, () ->
                vexPayService.createCheckoutSession(
                        testTutor.getId(),
                        false,
                        CreateVexPaySessionRequest.builder()
                                .athleteId(testAthlete.getId())
                                .planId("PRO_MONTHLY")
                                .build()
                ));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex503.getStatus());
        assertEquals("rate_unavailable", ex503.getErrorCode());
    }

    @Test
    void handleWebhook_InvalidOrExpiredSignature_RejectsRequest() {
        String payload = "{\"event\":\"payment.completed\",\"data\":{}}";

        VexPayApiException invalidSig = assertThrows(VexPayApiException.class, () ->
                vexPayService.handleWebhook(payload, "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef", "evt_1"));
        assertEquals("invalid_signature", invalidSig.getErrorCode());

        long expiredTs = Instant.now().minusSeconds(600).getEpochSecond();
        String expiredHeader = buildSignatureHeader(expiredTs, payload, properties.getWebhookSecret());
        VexPayApiException expiredSig = assertThrows(VexPayApiException.class, () ->
                vexPayService.handleWebhook(payload, expiredHeader, "evt_2"));
        assertEquals("signature_expired", expiredSig.getErrorCode());
    }

    @Test
    void handleWebhook_PaymentCompleted_ActivatesSubscriptionAndDeduplicatesByEventId() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.VEXPAY)
                .amountUsd(new BigDecimal("10.00"))
                .status(PaymentStatus.PENDING_GATEWAY)
                .vexpaySessionId("cs_999")
                .build();

        String payload = """
                {
                  "event": "payment.completed",
                  "data": {
                    "paymentId": "pay_001",
                    "method": "PAGO_MOVIL",
                    "vesAmount": 9157.48,
                    "rate": 915.7476,
                    "bcvRate": 915.7476,
                    "livemode": false,
                    "checkoutSession": {
                      "id": "cs_999",
                      "reference": "%s"
                    }
                  }
                }
                """.formatted(txId);

        long nowTs = Instant.now().getEpochSecond();
        String sig = buildSignatureHeader(nowTs, payload, properties.getWebhookSecret());

        when(webhookEventRepository.existsByEventId("evt_completed_1")).thenReturn(false).thenReturn(true);
        when(paymentTransactionRepository.findByVexpaySessionId("cs_999")).thenReturn(Optional.of(tx));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(
                testAthlete.getId(), SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(athleteSubscriptionRepository.save(any(AthleteSubscription.class))).thenAnswer(inv -> {
            AthleteSubscription sub = inv.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        Map<String, Object> firstResp = vexPayService.handleWebhook(payload, sig, "evt_completed_1");
        assertEquals(true, firstResp.get("received"));
        assertEquals(PaymentStatus.APPROVED, tx.getStatus());
        assertEquals("pay_001", tx.getVexpayPaymentId());
        assertEquals("PAGO_MOVIL", tx.getVexpaySubMethod());
        assertNotNull(tx.getAppliedSubscriptionId());
        verify(athleteSubscriptionRepository, times(1)).save(any(AthleteSubscription.class));
        verify(emailService, times(1)).sendPaymentStatusNotification(
                eq("tutor@vexpay.test"), anyString(), anyString(), anyString(), eq(true), isNull());

        // Segunda entrega con el mismo eventId debe ser ignorada sin re-activar
        Map<String, Object> dupResp = vexPayService.handleWebhook(payload, sig, "evt_completed_1");
        assertEquals(true, dupResp.get("deduplicated"));
        verify(athleteSubscriptionRepository, times(1)).save(any(AthleteSubscription.class));
    }

    @Test
    void handleWebhook_PaymentReversed_RestoresPreviousEndDateOnExtendedSubscription() {
        UUID txId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        Instant originalEnd = Instant.now().plus(15, ChronoUnit.DAYS);
        Instant extendedEnd = originalEnd.plus(30, ChronoUnit.DAYS);

        AthleteSubscription activeSub = AthleteSubscription.builder()
                .id(subId)
                .athlete(testAthlete)
                .plan(testPlan)
                .endDate(extendedEnd)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.VEXPAY)
                .amountUsd(new BigDecimal("10.00"))
                .status(PaymentStatus.APPROVED)
                .vexpaySessionId("cs_rev")
                .vexpayPaymentId("pay_rev")
                .appliedSubscriptionId(subId)
                .previousSubscriptionEndDate(originalEnd)
                .build();

        String payload = """
                {
                  "event": "payment.reversed",
                  "data": {
                    "paymentId": "pay_rev",
                    "reversalType": "reversal",
                    "reversalRef": "REV-991",
                    "livemode": false,
                    "checkoutSession": { "id": "cs_rev" }
                  }
                }
                """;
        String sig = buildSignatureHeader(Instant.now().getEpochSecond(), payload, properties.getWebhookSecret());

        when(webhookEventRepository.existsByEventId("evt_rev_1")).thenReturn(false);
        when(paymentTransactionRepository.findByVexpaySessionId("cs_rev")).thenReturn(Optional.of(tx));
        when(athleteSubscriptionRepository.findById(subId)).thenReturn(Optional.of(activeSub));

        vexPayService.handleWebhook(payload, sig, "evt_rev_1");

        assertEquals(PaymentStatus.REVERSED, tx.getStatus());
        assertEquals(originalEnd, activeSub.getEndDate());
        assertEquals(SubscriptionStatus.ACTIVE, activeSub.getStatus());
        verify(emailService, times(1)).sendPaymentReversedNotification(
                eq("tutor@vexpay.test"), anyString(), anyString(), anyString(), eq("reversal"), eq("REV-991"));
    }

    @Test
    void handleWebhook_PaymentReversed_CancelsNewSubscription() {
        UUID txId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        AthleteSubscription newSub = AthleteSubscription.builder()
                .id(subId)
                .athlete(testAthlete)
                .plan(testPlan)
                .endDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(SubscriptionStatus.ACTIVE)
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.VEXPAY)
                .amountUsd(new BigDecimal("10.00"))
                .status(PaymentStatus.APPROVED)
                .vexpaySessionId("cs_rev_new")
                .appliedSubscriptionId(subId)
                .previousSubscriptionEndDate(null)
                .build();

        String payload = """
                {
                  "event": "payment.reversed",
                  "data": {
                    "paymentId": "pay_rev_new",
                    "reversalType": "chargeback",
                    "reversalRef": "CB-100",
                    "checkoutSession": { "id": "cs_rev_new" }
                  }
                }
                """;
        String sig = buildSignatureHeader(Instant.now().getEpochSecond(), payload, properties.getWebhookSecret());

        when(webhookEventRepository.existsByEventId("evt_rev_2")).thenReturn(false);
        when(paymentTransactionRepository.findByVexpaySessionId("cs_rev_new")).thenReturn(Optional.of(tx));
        when(athleteSubscriptionRepository.findById(subId)).thenReturn(Optional.of(newSub));

        vexPayService.handleWebhook(payload, sig, "evt_rev_2");

        assertEquals(PaymentStatus.REVERSED, tx.getStatus());
        assertEquals(SubscriptionStatus.CANCELLED, newSub.getStatus());
    }

    @Test
    void verifyCheckoutSession_AndReconcile_ResolvesPaidAndExpired() {
        PaymentTransaction txPaid = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.VEXPAY)
                .amountUsd(new BigDecimal("10.00"))
                .status(PaymentStatus.PENDING_GATEWAY)
                .vexpaySessionId("cs_paid_1")
                .build();

        PaymentTransaction txExpired = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.VEXPAY)
                .amountUsd(new BigDecimal("10.00"))
                .status(PaymentStatus.PENDING_GATEWAY)
                .vexpaySessionId("cs_exp_1")
                .build();

        when(paymentTransactionRepository.findByMethodAndStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                eq(PaymentMethod.VEXPAY), eq(PaymentStatus.PENDING_GATEWAY), any()))
                .thenReturn(List.of(txPaid, txExpired));

        when(restTemplate.exchange(
                eq("https://api.vexpay.app/v1/checkout/sessions/cs_paid_1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {"id":"cs_paid_1","status":"paid","paymentId":"pay_c2p_1"}
                """));

        when(restTemplate.exchange(
                eq("https://api.vexpay.app/v1/payments/pay_c2p_1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {"paymentId":"pay_c2p_1","status":"COMPLETED","method":"C2P","vesAmount":9157.48,"rate":915.7476}
                """));

        when(restTemplate.exchange(
                eq("https://api.vexpay.app/v1/checkout/sessions/cs_exp_1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {"id":"cs_exp_1","status":"expired"}
                """));

        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(
                testAthlete.getId(), SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(athleteSubscriptionRepository.save(any())).thenAnswer(inv -> {
            AthleteSubscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        VexPayReconciliationResponse result = vexPayService.reconcilePendingTransactions();

        assertEquals(2, result.getCheckedCount());
        assertEquals(1, result.getApprovedCount());
        assertEquals(1, result.getRejectedCount());
        assertEquals(PaymentStatus.APPROVED, txPaid.getStatus());
        assertEquals("C2P", txPaid.getVexpaySubMethod());
        assertEquals(PaymentStatus.REJECTED, txExpired.getStatus());
    }

    private static String buildSignatureHeader(long timestamp, String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return "t=" + timestamp + ",v1=" + sb;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
