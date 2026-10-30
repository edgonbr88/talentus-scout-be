package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.DiscountType;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.repository.*;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import com.talentus.scout.web.dto.RedeemFreePromoRequest;
import com.talentus.scout.web.dto.ValidateCouponRequest;
import com.talentus.scout.web.dto.ValidateCouponResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountCodeService {

    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountCodeRedemptionRepository discountCodeRedemptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final BcvRateService bcvRateService;

    @Transactional(readOnly = true)
    public ValidateCouponResponse validateCoupon(ValidateCouponRequest request) {
        return validateCoupon(request.getCode(), request.getAthleteId(), request.getPlanId());
    }

    @Transactional(readOnly = true)
    public ValidateCouponResponse validateCoupon(String rawCode, UUID athleteId, String planId) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .message("El código de descuento no puede estar vacío.")
                    .build();
        }

        String normalizedCode = rawCode.trim().toUpperCase();
        Optional<DiscountCode> codeOpt = discountCodeRepository.findByCodeIgnoreCase(normalizedCode);
        if (codeOpt.isEmpty()) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(normalizedCode)
                    .message("El código '" + normalizedCode + "' no existe o no es válido.")
                    .build();
        }

        DiscountCode discountCode = codeOpt.get();

        if (!Boolean.TRUE.equals(discountCode.getIsActive())) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(discountCode.getCode())
                    .message("El cupón '" + discountCode.getCode() + "' está actualmente inactivo.")
                    .build();
        }

        Instant now = Instant.now();
        if (discountCode.getStartsAt() != null && discountCode.getStartsAt().isAfter(now)) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(discountCode.getCode())
                    .message("El cupón aún no ha iniciado su vigencia promocional.")
                    .build();
        }

        if (discountCode.getExpiresAt() != null && discountCode.getExpiresAt().isBefore(now)) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(discountCode.getCode())
                    .message("El cupón ha expirado.")
                    .build();
        }

        if (discountCode.getMaxUses() != null && discountCode.getCurrentUses() >= discountCode.getMaxUses()) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(discountCode.getCode())
                    .message("El límite máximo de usos de este cupón ha sido alcanzado.")
                    .build();
        }

        if (discountCode.getApplicablePlan() != null &&
                !discountCode.getApplicablePlan().getId().equalsIgnoreCase(planId)) {
            return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(discountCode.getCode())
                    .message("Este cupón es exclusivo para el plan: " + discountCode.getApplicablePlan().getName())
                    .build();
        }

        if (athleteId != null && Boolean.TRUE.equals(discountCode.getOneUsePerAthlete())) {
            boolean alreadyRedeemed = discountCodeRedemptionRepository
                    .existsByDiscountCodeIdAndAthleteId(discountCode.getId(), athleteId);
            if (alreadyRedeemed) {
                return ValidateCouponResponse.builder()
                    .valid(false)
                    .code(discountCode.getCode())
                    .message("Este cupón ya fue canjeado previamente para este atleta.")
                    .build();
            }
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElse(null);
        BigDecimal originalAmountUsd = plan != null
                ? plan.getPriceUsd().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(5.00).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountAmountUsd = BigDecimal.ZERO;
        if (discountCode.getDiscountType() == DiscountType.PERCENTAGE) {
            BigDecimal percentage = discountCode.getDiscountValue()
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discountAmountUsd = originalAmountUsd.multiply(percentage).setScale(2, RoundingMode.HALF_UP);
            if (discountAmountUsd.compareTo(originalAmountUsd) > 0) {
                discountAmountUsd = originalAmountUsd;
            }
        } else if (discountCode.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            discountAmountUsd = discountCode.getDiscountValue().min(originalAmountUsd).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal finalAmountUsd = originalAmountUsd.subtract(discountAmountUsd)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal bcvRate = BigDecimal.valueOf(36.50);
        try {
            bcvRate = bcvRateService.getOfficialRate().getRate();
        } catch (Exception e) {
            log.warn("No se pudo obtener tasa BCV en tiempo real, usando fallback {}: {}", bcvRate, e.getMessage());
        }

        BigDecimal finalAmountVes = finalAmountUsd.multiply(bcvRate).setScale(2, RoundingMode.HALF_UP);
        boolean isFreePromo = finalAmountUsd.compareTo(BigDecimal.ZERO) == 0;

        String orgName = discountCode.getOrganization() != null ? discountCode.getOrganization().getName() : null;
        UUID orgId = discountCode.getOrganization() != null ? discountCode.getOrganization().getId() : null;

        String successMsg = isFreePromo
                ? "¡Beneficio del 100% activado! Cupón institucional aplicado con éxito."
                : "Cupón aplicado: Descuento de $" + discountAmountUsd + " USD.";

        return ValidateCouponResponse.builder()
                .valid(true)
                .message(successMsg)
                .code(discountCode.getCode())
                .discountType(discountCode.getDiscountType())
                .discountValue(discountCode.getDiscountValue())
                .originalAmountUsd(originalAmountUsd)
                .discountAmountUsd(discountAmountUsd)
                .finalAmountUsd(finalAmountUsd)
                .finalAmountVes(finalAmountVes)
                .bcvExchangeRate(bcvRate)
                .isFreePromo(isFreePromo)
                .organizationId(orgId)
                .organizationName(orgName)
                .build();
    }

    @Transactional
    public PaymentTransactionResponse redeemFreePromo(UUID tutorUserId, RedeemFreePromoRequest request) {
        ValidateCouponResponse validation = validateCoupon(request.getCode(), request.getAthleteId(), request.getPlanId());
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }

        if (!validation.isFreePromo()) {
            throw new IllegalArgumentException("El cupón no cubre el 100% de la suscripción. Debe completar el pago vía Pago Móvil o Binance Pay.");
        }

        User tutor = userRepository.findById(tutorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario tutor no encontrado con ID: " + tutorUserId));

        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new IllegalArgumentException("Atleta no encontrado con ID: " + request.getAthleteId()));

        if (athlete.getTutor() != null && !athlete.getTutor().getId().equals(tutorUserId)) {
            throw new SecurityException("No tienes permisos para canjear promociones para este atleta.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + request.getPlanId()));

        DiscountCode discountCode = discountCodeRepository.findByCodeIgnoreCase(request.getCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Código no encontrado"));

        Instant now = Instant.now();

        // 1. Crear y registrar transacción de pago 100% bonificada
        PaymentTransaction tx = PaymentTransaction.builder()
                .user(tutor)
                .athlete(athlete)
                .plan(plan)
                .method(PaymentMethod.DISCOUNT_CODE)
                .amountUsd(BigDecimal.ZERO)
                .amountVes(BigDecimal.ZERO)
                .bcvExchangeRate(validation.getBcvExchangeRate())
                .referenceNumber("PROMO-" + discountCode.getCode() + "-" + System.currentTimeMillis())
                .paymentDate(LocalDate.now())
                .originBank("CONVENIO / BECA DEPORTIVA")
                .status(PaymentStatus.APPROVED)
                .discountCode(discountCode)
                .discountAmountUsd(validation.getOriginalAmountUsd())
                .auditedAt(now)
                .build();

        PaymentTransaction savedTx = paymentTransactionRepository.save(tx);

        // 2. Activar o extender la suscripción del atleta
        Optional<AthleteSubscription> existingActive = athleteSubscriptionRepository
                .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE);

        if (existingActive.isPresent() && existingActive.get().getEndDate().isAfter(now)) {
            AthleteSubscription activeSub = existingActive.get();
            Instant extendedEnd = activeSub.getEndDate().plus(Duration.ofDays(plan.getDurationDays()));
            activeSub.setEndDate(extendedEnd);
            activeSub.setPlan(plan);
            athleteSubscriptionRepository.save(activeSub);
            log.info("Suscripción de atleta {} extendida hasta {} mediante cupón 100% {}", athlete.getId(), extendedEnd, discountCode.getCode());
        } else {
            AthleteSubscription newSub = AthleteSubscription.builder()
                    .athlete(athlete)
                    .plan(plan)
                    .startDate(now)
                    .endDate(now.plus(Duration.ofDays(plan.getDurationDays())))
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            athleteSubscriptionRepository.save(newSub);
            log.info("Suscripción de atleta {} activada inmediatamente mediante cupón 100% {}", athlete.getId(), discountCode.getCode());
        }

        // 3. Registrar la redención para trazabilidad y auditoría
        DiscountCodeRedemption redemption = DiscountCodeRedemption.builder()
                .discountCode(discountCode)
                .user(tutor)
                .athlete(athlete)
                .paymentTransaction(savedTx)
                .originalAmountUsd(validation.getOriginalAmountUsd())
                .discountedAmountUsd(validation.getDiscountAmountUsd())
                .finalAmountUsd(BigDecimal.ZERO)
                .redeemedAt(now)
                .build();
        discountCodeRedemptionRepository.save(redemption);

        // 4. Incrementar usos del cupón
        discountCode.setCurrentUses(discountCode.getCurrentUses() + 1);
        discountCodeRepository.save(discountCode);

        return mapToResponse(savedTx);
    }

    private PaymentTransactionResponse mapToResponse(PaymentTransaction tx) {
        String tutorName = tx.getUser() != null ? tx.getUser().getFullName() : null;
        String tutorEmail = tx.getUser() != null ? tx.getUser().getEmail() : null;
        String tutorPhone = tx.getUser() != null ? tx.getUser().getPhoneNumber() : null;

        return PaymentTransactionResponse.builder()
                .id(tx.getId())
                .athleteId(tx.getAthlete().getId())
                .athleteName(tx.getAthlete().getFirstName() + " " + tx.getAthlete().getLastName())
                .planId(tx.getPlan().getId())
                .planName(tx.getPlan().getName())
                .method(tx.getMethod())
                .amountUsd(tx.getAmountUsd())
                .amountVes(tx.getAmountVes())
                .bcvExchangeRate(tx.getBcvExchangeRate())
                .referenceNumber(tx.getReferenceNumber())
                .paymentDate(tx.getPaymentDate())
                .originBank(tx.getOriginBank())
                .originPhone(tx.getOriginPhone())
                .status(tx.getStatus())
                .rejectionReason(tx.getRejectionReason())
                .createdAt(tx.getCreatedAt())
                .auditedAt(tx.getAuditedAt())
                .tutorName(tutorName)
                .tutorEmail(tutorEmail)
                .tutorPhone(tutorPhone)
                .discountCode(tx.getDiscountCode() != null ? tx.getDiscountCode().getCode() : null)
                .discountAmountUsd(tx.getDiscountAmountUsd())
                .build();
    }
}
