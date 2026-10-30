package com.talentus.scout.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteSubscription;
import com.talentus.scout.domain.entity.PaymentTransaction;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.PaymentTransactionRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.BinanceWebhookResponse;
import com.talentus.scout.web.dto.CreateBinanceOrderRequest;
import com.talentus.scout.web.dto.CreateBinanceOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BinancePayServiceTest {

    private PaymentTransactionRepository paymentTransactionRepository;
    private AthleteSubscriptionRepository athleteSubscriptionRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private AthleteRepository athleteRepository;
    private EmailService emailService;
    private ObjectMapper objectMapper;
    private BinancePayService binancePayService;

    private User testTutor;
    private Athlete testAthlete;
    private SubscriptionPlan testPlan;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        athleteSubscriptionRepository = mock(AthleteSubscriptionRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        athleteRepository = mock(AthleteRepository.class);
        emailService = mock(EmailService.class);
        objectMapper = new ObjectMapper();

        binancePayService = new BinancePayService(
                paymentTransactionRepository,
                athleteSubscriptionRepository,
                subscriptionPlanRepository,
                athleteRepository,
                emailService,
                objectMapper
        );

        ReflectionTestUtils.setField(binancePayService, "secretKey", "test_secret_key");
        ReflectionTestUtils.setField(binancePayService, "sandbox", true);

        testTutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@binance.test")
                .fullName("Roberto Tutor")
                .role(UserRole.TUTOR)
                .build();

        testAthlete = Athlete.builder()
                .id(UUID.randomUUID())
                .firstName("Mateo")
                .lastName("Silva")
                .tutor(testTutor)
                .build();

        testPlan = SubscriptionPlan.builder()
                .id("PRO_MONTHLY")
                .name("Plan Pro Mensual")
                .priceUsd(new BigDecimal("5.00"))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    @Test
    void createOrder_Success_GeneratesOrderAndPendingTransaction() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        CreateBinanceOrderRequest request = CreateBinanceOrderRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .build();

        CreateBinanceOrderResponse response = binancePayService.createOrder(testTutor.getId(), request);

        assertNotNull(response);
        assertNotNull(response.getTransactionId());
        assertNotNull(response.getGatewayOrderId());
        assertEquals("USDT", response.getCurrency());
        assertEquals(new BigDecimal("5.00"), response.getAmountUsd());
        assertTrue(response.getGatewayOrderId().startsWith("TS-"));
    }

    @Test
    void createOrder_UnauthorizedTutor_ThrowsForbidden() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));

        CreateBinanceOrderRequest request = CreateBinanceOrderRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                binancePayService.createOrder(UUID.randomUUID(), request));
    }

    @Test
    void handleWebhook_ValidSignatureAndSuccess_ActivatesSubscription() {
        String tradeNo = "TS-TEST123456";
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.BINANCE_PAY)
                .gatewayOrderId(tradeNo)
                .status(PaymentStatus.PENDING_AUDIT)
                .build();

        when(paymentTransactionRepository.findByGatewayOrderId(tradeNo)).thenReturn(Optional.of(tx));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(testAthlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        String payload = "{\"bizType\":\"PAY\",\"bizId\":\"" + tradeNo + "\",\"bizStatus\":\"PAY_SUCCESS\"}";

        BinanceWebhookResponse response = binancePayService.handleWebhook(payload, "mock-valid-sig", "1710000000", "nonce123");

        assertEquals("SUCCESS", response.getReturnCode());
        assertEquals(PaymentStatus.APPROVED, tx.getStatus());
        verify(athleteSubscriptionRepository).save(any(AthleteSubscription.class));
        verify(paymentTransactionRepository).save(tx);
    }

    @Test
    void handleWebhook_ExistingActiveSubscription_ExtendsEndDate() {
        String tradeNo = "TS-EXTEND999";
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.BINANCE_PAY)
                .gatewayOrderId(tradeNo)
                .status(PaymentStatus.PENDING_AUDIT)
                .build();

        Instant currentEnd = Instant.now().plus(10, ChronoUnit.DAYS);
        AthleteSubscription currentSub = AthleteSubscription.builder()
                .id(UUID.randomUUID())
                .athlete(testAthlete)
                .plan(testPlan)
                .startDate(Instant.now().minus(20, ChronoUnit.DAYS))
                .endDate(currentEnd)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(paymentTransactionRepository.findByGatewayOrderId(tradeNo)).thenReturn(Optional.of(tx));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(testAthlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(currentSub));

        String payload = "{\"bizType\":\"PAY\",\"bizId\":\"" + tradeNo + "\",\"bizStatus\":\"PAY_SUCCESS\"}";

        BinanceWebhookResponse response = binancePayService.handleWebhook(payload, "mock-valid-sig", "1710000000", "nonce123");

        assertEquals("SUCCESS", response.getReturnCode());
        assertTrue(currentSub.getEndDate().isAfter(currentEnd));
        verify(athleteSubscriptionRepository).save(currentSub);
    }

    @Test
    void handleWebhook_InvalidSignature_ReturnsFail() {
        ReflectionTestUtils.setField(binancePayService, "sandbox", false);

        String payload = "{\"bizType\":\"PAY\",\"bizId\":\"TS-FAIL\",\"bizStatus\":\"PAY_SUCCESS\"}";
        BinanceWebhookResponse response = binancePayService.handleWebhook(payload, "invalid-sig", "1710000000", "nonce123");

        assertEquals("FAIL", response.getReturnCode());
        verifyNoInteractions(paymentTransactionRepository);
    }
}
