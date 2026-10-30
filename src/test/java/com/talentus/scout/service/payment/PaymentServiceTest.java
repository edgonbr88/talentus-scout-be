package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.*;
import com.talentus.scout.web.dto.AthleteSubscriptionStatusResponse;
import com.talentus.scout.web.dto.BcvRateResponse;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import com.talentus.scout.web.dto.ReportPagoMovilRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentTransactionRepository paymentTransactionRepository;
    private AthleteSubscriptionRepository athleteSubscriptionRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private AthleteRepository athleteRepository;
    private UserRepository userRepository;
    private BcvRateService bcvRateService;
    private com.talentus.scout.repository.DiscountCodeRepository discountCodeRepository;
    private com.talentus.scout.repository.DiscountCodeRedemptionRepository discountCodeRedemptionRepository;
    private PaymentService paymentService;

    private User testTutor;
    private Athlete testAthlete;
    private SubscriptionPlan testPlan;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        athleteSubscriptionRepository = mock(AthleteSubscriptionRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        athleteRepository = mock(AthleteRepository.class);
        userRepository = mock(UserRepository.class);
        bcvRateService = mock(BcvRateService.class);
        discountCodeRepository = mock(com.talentus.scout.repository.DiscountCodeRepository.class);
        discountCodeRedemptionRepository = mock(com.talentus.scout.repository.DiscountCodeRedemptionRepository.class);

        paymentService = new PaymentService(
                paymentTransactionRepository,
                athleteSubscriptionRepository,
                subscriptionPlanRepository,
                athleteRepository,
                userRepository,
                bcvRateService,
                discountCodeRepository,
                discountCodeRedemptionRepository
        );

        testTutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@example.com")
                .fullName("Carlos Tutor")
                .phoneNumber("+584121234567")
                .role(UserRole.TUTOR)
                .build();

        testAthlete = Athlete.builder()
                .id(UUID.randomUUID())
                .firstName("Luis")
                .lastName("Gómez")
                .tutor(testTutor)
                .slug("luis-gomez")
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
    void reportPagoMovil_Success_CreatesPendingAuditTransaction() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));
        when(bcvRateService.getOfficialRate()).thenReturn(BcvRateResponse.builder()
                .rate(new BigDecimal("45.50"))
                .build());

        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(Instant.now());
            return tx;
        });

        ReportPagoMovilRequest request = ReportPagoMovilRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber("091823")
                .paymentDate(LocalDate.now())
                .originBank("Banesco")
                .amountVes(new BigDecimal("227.50"))
                .build();

        PaymentTransactionResponse response = paymentService.reportPagoMovil(testTutor.getId(), request);

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING_AUDIT, response.getStatus());
        assertEquals("091823", response.getReferenceNumber());
        assertEquals(new BigDecimal("5.00"), response.getAmountUsd());
        assertEquals(new BigDecimal("227.50"), response.getAmountVes());
        verify(paymentTransactionRepository, times(1)).save(any(PaymentTransaction.class));
    }

    @Test
    void reportPagoMovil_WithoutAmountVes_CalculatesFallbackAutomatically() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));
        when(bcvRateService.getOfficialRate()).thenReturn(BcvRateResponse.builder()
                .rate(new BigDecimal("45.50"))
                .build());

        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(Instant.now());
            return tx;
        });

        // Petición sin amountVes (null) y sin bcvRate (debe usar bcvRateService)
        ReportPagoMovilRequest request = ReportPagoMovilRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber("771122")
                .paymentDate(LocalDate.now())
                .originBank("Mercantil")
                .amountVes(null)
                .bcvRate(null)
                .build();

        PaymentTransactionResponse response = paymentService.reportPagoMovil(testTutor.getId(), request);

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING_AUDIT, response.getStatus());
        assertEquals("771122", response.getReferenceNumber());
        assertEquals(new BigDecimal("5.00"), response.getAmountUsd());
        // 5.00 * 45.50 = 227.50
        assertEquals(new BigDecimal("227.50"), response.getAmountVes());
        verify(paymentTransactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
    }

    @Test
    void reportPagoMovil_WithCustomBcvRateAndZeroAmountVes_CalculatesWithCustomRate() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(testPlan));

        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(Instant.now());
            return tx;
        });

        // Petición con bcvRate provisto pero amountVes no enviado (null)
        ReportPagoMovilRequest request = ReportPagoMovilRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber("334455")
                .paymentDate(LocalDate.now())
                .originBank("BBVA")
                .bcvRate(new BigDecimal("50.00"))
                .build();

        PaymentTransactionResponse response = paymentService.reportPagoMovil(testTutor.getId(), request);

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING_AUDIT, response.getStatus());
        assertEquals(new BigDecimal("5.00"), response.getAmountUsd());
        // 5.00 * 50.00 = 250.00
        assertEquals(new BigDecimal("250.00"), response.getAmountVes());
    }

    @Test
    void reportPagoMovil_WrongTutor_ThrowsAccessDeniedException() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));

        ReportPagoMovilRequest request = ReportPagoMovilRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber("091823")
                .amountVes(new BigDecimal("227.50"))
                .build();

        UUID foreignTutorId = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> paymentService.reportPagoMovil(foreignTutorId, request));
    }

    @Test
    void getAthleteSubscriptionStatus_ActiveSubscription_ReturnsActive() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));

        Instant endDate = Instant.now().plus(20, ChronoUnit.DAYS);
        AthleteSubscription sub = AthleteSubscription.builder()
                .id(UUID.randomUUID())
                .athlete(testAthlete)
                .plan(testPlan)
                .startDate(Instant.now().minus(10, ChronoUnit.DAYS))
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(testAthlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(sub));

        AthleteSubscriptionStatusResponse status = paymentService.getAthleteSubscriptionStatus(testAthlete.getId(), testTutor.getId(), false);

        assertNotNull(status);
        assertEquals("ACTIVE", status.getStatus());
        assertTrue(status.isVisibleInScoutDirectory());
        assertTrue(status.getDaysRemaining() >= 19);
    }

    @Test
    void getAthleteSubscriptionStatus_PendingAudit_ReturnsPendingAudit() {
        when(athleteRepository.findById(testAthlete.getId())).thenReturn(Optional.of(testAthlete));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(testAthlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .athlete(testAthlete)
                .plan(testPlan)
                .status(PaymentStatus.PENDING_AUDIT)
                .build();

        when(paymentTransactionRepository.findFirstByAthleteIdAndStatusOrderByCreatedAtDesc(testAthlete.getId(), PaymentStatus.PENDING_AUDIT))
                .thenReturn(Optional.of(tx));

        AthleteSubscriptionStatusResponse status = paymentService.getAthleteSubscriptionStatus(testAthlete.getId(), testTutor.getId(), false);

        assertNotNull(status);
        assertEquals("PENDING_AUDIT", status.getStatus());
        assertFalse(status.isVisibleInScoutDirectory());
    }
}
