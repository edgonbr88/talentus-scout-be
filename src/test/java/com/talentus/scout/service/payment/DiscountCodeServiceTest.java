package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.DiscountType;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.repository.*;
import com.talentus.scout.web.dto.BcvRateResponse;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import com.talentus.scout.web.dto.RedeemFreePromoRequest;
import com.talentus.scout.web.dto.ValidateCouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountCodeServiceTest {

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    @Mock
    private DiscountCodeRedemptionRepository discountCodeRedemptionRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private AthleteSubscriptionRepository athleteSubscriptionRepository;

    @Mock
    private BcvRateService bcvRateService;

    @InjectMocks
    private DiscountCodeService discountCodeService;

    private SubscriptionPlan proMonthlyPlan;
    private Athlete athlete;
    private User tutor;
    private UUID athleteId;
    private UUID tutorId;

    @BeforeEach
    void setUp() {
        athleteId = UUID.randomUUID();
        tutorId = UUID.randomUUID();

        tutor = User.builder()
                .id(tutorId)
                .email("tutor@talentus.app")
                .fullName("Carlos Tutor")
                .build();

        athlete = Athlete.builder()
                .id(athleteId)
                .firstName("Samuel")
                .lastName("Rondón")
                .tutor(tutor)
                .build();

        proMonthlyPlan = SubscriptionPlan.builder()
                .id("PRO_MONTHLY")
                .name("Plan Pro Mensual")
                .priceUsd(BigDecimal.valueOf(5.00))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Validar cupón porcentual del 20% calcula descuento exacto en USD y VES")
    void validateCoupon_percentageSuccess() {
        DiscountCode code = DiscountCode.builder()
                .id(UUID.randomUUID())
                .code("PROMO20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .isActive(true)
                .currentUses(0)
                .maxUses(100)
                .oneUsePerAthlete(true)
                .build();

        when(discountCodeRepository.findByCodeIgnoreCase("PROMO20")).thenReturn(Optional.of(code));
        when(discountCodeRedemptionRepository.existsByDiscountCodeIdAndAthleteId(code.getId(), athleteId)).thenReturn(false);
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(proMonthlyPlan));
        when(bcvRateService.getOfficialRate()).thenReturn(BcvRateResponse.builder().rate(BigDecimal.valueOf(40.00)).source("BCV").build());

        ValidateCouponResponse response = discountCodeService.validateCoupon("PROMO20", athleteId, "PRO_MONTHLY");

        assertTrue(response.isValid());
        assertEquals(new BigDecimal("5.00"), response.getOriginalAmountUsd());
        assertEquals(new BigDecimal("1.00"), response.getDiscountAmountUsd());
        assertEquals(new BigDecimal("4.00"), response.getFinalAmountUsd());
        assertEquals(new BigDecimal("160.00"), response.getFinalAmountVes());
        assertFalse(response.isFreePromo());
    }

    @Test
    @DisplayName("Validar cupón 100% de beca institucional reconoce beneficio gratuito")
    void validateCoupon_freePromo100Percent() {
        ClubOrganization org = ClubOrganization.builder()
                .id(UUID.randomUUID())
                .name("Asociación de Fútbol de Distrito Capital")
                .build();

        DiscountCode code = DiscountCode.builder()
                .id(UUID.randomUUID())
                .code("AFDC100")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(100))
                .organization(org)
                .isActive(true)
                .currentUses(5)
                .maxUses(50)
                .oneUsePerAthlete(true)
                .build();

        when(discountCodeRepository.findByCodeIgnoreCase("AFDC100")).thenReturn(Optional.of(code));
        when(discountCodeRedemptionRepository.existsByDiscountCodeIdAndAthleteId(code.getId(), athleteId)).thenReturn(false);
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(proMonthlyPlan));
        when(bcvRateService.getOfficialRate()).thenReturn(BcvRateResponse.builder().rate(BigDecimal.valueOf(40.00)).source("BCV").build());

        ValidateCouponResponse response = discountCodeService.validateCoupon("AFDC100", athleteId, "PRO_MONTHLY");

        assertTrue(response.isValid());
        assertTrue(response.isFreePromo());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getFinalAmountUsd());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getFinalAmountVes());
        assertEquals("Asociación de Fútbol de Distrito Capital", response.getOrganizationName());
    }

    @Test
    @DisplayName("Rechazar cupón inexistente")
    void validateCoupon_notFound() {
        when(discountCodeRepository.findByCodeIgnoreCase("INEXISTENTE")).thenReturn(Optional.empty());

        ValidateCouponResponse response = discountCodeService.validateCoupon("INEXISTENTE", athleteId, "PRO_MONTHLY");

        assertFalse(response.isValid());
        assertTrue(response.getMessage().contains("no existe o no es válido"));
    }

    @Test
    @DisplayName("Rechazar cupón inactivo")
    void validateCoupon_inactive() {
        DiscountCode code = DiscountCode.builder()
                .code("INACTIVO")
                .isActive(false)
                .build();
        when(discountCodeRepository.findByCodeIgnoreCase("INACTIVO")).thenReturn(Optional.of(code));

        ValidateCouponResponse response = discountCodeService.validateCoupon("INACTIVO", athleteId, "PRO_MONTHLY");

        assertFalse(response.isValid());
        assertTrue(response.getMessage().contains("inactivo"));
    }

    @Test
    @DisplayName("Rechazar cupón expirado")
    void validateCoupon_expired() {
        DiscountCode code = DiscountCode.builder()
                .code("EXPIRADO")
                .isActive(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(discountCodeRepository.findByCodeIgnoreCase("EXPIRADO")).thenReturn(Optional.of(code));

        ValidateCouponResponse response = discountCodeService.validateCoupon("EXPIRADO", athleteId, "PRO_MONTHLY");

        assertFalse(response.isValid());
        assertTrue(response.getMessage().contains("expirado"));
    }

    @Test
    @DisplayName("Rechazar cupón con usos agotados")
    void validateCoupon_maxUsesReached() {
        DiscountCode code = DiscountCode.builder()
                .code("AGOTADO")
                .isActive(true)
                .currentUses(10)
                .maxUses(10)
                .build();
        when(discountCodeRepository.findByCodeIgnoreCase("AGOTADO")).thenReturn(Optional.of(code));

        ValidateCouponResponse response = discountCodeService.validateCoupon("AGOTADO", athleteId, "PRO_MONTHLY");

        assertFalse(response.isValid());
        assertTrue(response.getMessage().contains("límite máximo de usos"));
    }

    @Test
    @DisplayName("Rechazar cupón ya canjeado por el mismo atleta")
    void validateCoupon_alreadyRedeemedByAthlete() {
        DiscountCode code = DiscountCode.builder()
                .id(UUID.randomUUID())
                .code("UNUSO")
                .isActive(true)
                .oneUsePerAthlete(true)
                .build();
        when(discountCodeRepository.findByCodeIgnoreCase("UNUSO")).thenReturn(Optional.of(code));
        when(discountCodeRedemptionRepository.existsByDiscountCodeIdAndAthleteId(code.getId(), athleteId)).thenReturn(true);

        ValidateCouponResponse response = discountCodeService.validateCoupon("UNUSO", athleteId, "PRO_MONTHLY");

        assertFalse(response.isValid());
        assertTrue(response.getMessage().contains("ya fue canjeado"));
    }

    @Test
    @DisplayName("Canje 100% exitoso: crea transacción aprobada, activa suscripción y registra redención")
    void redeemFreePromo_success() {
        DiscountCode code = DiscountCode.builder()
                .id(UUID.randomUUID())
                .code("BECA100")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(100))
                .isActive(true)
                .currentUses(0)
                .maxUses(5)
                .oneUsePerAthlete(true)
                .build();

        when(discountCodeRepository.findByCodeIgnoreCase("BECA100")).thenReturn(Optional.of(code));
        when(discountCodeRedemptionRepository.existsByDiscountCodeIdAndAthleteId(code.getId(), athleteId)).thenReturn(false);
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(proMonthlyPlan));
        when(userRepository.findById(tutorId)).thenReturn(Optional.of(tutor));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(athleteId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(bcvRateService.getOfficialRate()).thenReturn(BcvRateResponse.builder().rate(BigDecimal.valueOf(40.00)).source("BCV").build());

        PaymentTransaction mockTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .user(tutor)
                .athlete(athlete)
                .plan(proMonthlyPlan)
                .method(PaymentMethod.DISCOUNT_CODE)
                .amountUsd(BigDecimal.ZERO)
                .status(PaymentStatus.APPROVED)
                .discountCode(code)
                .discountAmountUsd(BigDecimal.valueOf(5.00))
                .build();
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(mockTx);

        RedeemFreePromoRequest request = RedeemFreePromoRequest.builder()
                .code("BECA100")
                .athleteId(athleteId)
                .planId("PRO_MONTHLY")
                .build();

        PaymentTransactionResponse response = discountCodeService.redeemFreePromo(tutorId, request);

        assertNotNull(response);
        assertEquals(PaymentMethod.DISCOUNT_CODE, response.getMethod());
        assertEquals(PaymentStatus.APPROVED, response.getStatus());

        verify(athleteSubscriptionRepository).save(any(AthleteSubscription.class));
        verify(discountCodeRedemptionRepository).save(any(DiscountCodeRedemption.class));
        verify(discountCodeRepository).save(code);
        assertEquals(1, code.getCurrentUses());
    }

    @Test
    @DisplayName("Rechazar canje si el cupón no cubre el 100%")
    void redeemFreePromo_not100Percent() {
        DiscountCode code = DiscountCode.builder()
                .id(UUID.randomUUID())
                .code("PROMO50")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(50))
                .isActive(true)
                .build();

        when(discountCodeRepository.findByCodeIgnoreCase("PROMO50")).thenReturn(Optional.of(code));
        when(discountCodeRedemptionRepository.existsByDiscountCodeIdAndAthleteId(code.getId(), athleteId)).thenReturn(false);
        when(subscriptionPlanRepository.findById("PRO_MONTHLY")).thenReturn(Optional.of(proMonthlyPlan));

        RedeemFreePromoRequest request = RedeemFreePromoRequest.builder()
                .code("PROMO50")
                .athleteId(athleteId)
                .planId("PRO_MONTHLY")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                discountCodeService.redeemFreePromo(tutorId, request));

        assertTrue(ex.getMessage().contains("no cubre el 100%"));
    }
}
