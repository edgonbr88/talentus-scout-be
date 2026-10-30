package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteSubscription;
import com.talentus.scout.domain.entity.PaymentTransaction;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.PaymentTransactionRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.exception.DuplicatePaymentReferenceException;
import com.talentus.scout.web.dto.AthleteSubscriptionStatusResponse;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import com.talentus.scout.web.dto.ReportBinanceManualPaymentRequest;
import com.talentus.scout.web.dto.ReportPagoMovilRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final BcvRateService bcvRateService;
    private final com.talentus.scout.repository.DiscountCodeRepository discountCodeRepository;
    private final com.talentus.scout.repository.DiscountCodeRedemptionRepository discountCodeRedemptionRepository;

    @Transactional
    public PaymentTransactionResponse reportPagoMovil(UUID tutorId, ReportPagoMovilRequest request) {
        log.info("Procesando reporte de Pago Móvil para atleta {} por tutor {}", request.getAthleteId(), tutorId);

        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new IllegalArgumentException("El atleta especificado no existe."));

        if (!athlete.getTutor().getId().equals(tutorId)) {
            throw new AccessDeniedException("No tienes autorización para reportar pagos de este atleta.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("El plan seleccionado no es válido."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("El plan seleccionado no está activo actualmente.");
        }

        // Procesar código de descuento si fue suministrado
        com.talentus.scout.domain.entity.DiscountCode discountCode = null;
        BigDecimal discountAmountUsd = BigDecimal.ZERO;
        BigDecimal effectiveAmountUsd = plan.getPriceUsd();

        if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
            String codeStr = request.getDiscountCode().trim().toUpperCase();
            Optional<com.talentus.scout.domain.entity.DiscountCode> codeOpt = discountCodeRepository.findByCodeIgnoreCase(codeStr);
            if (codeOpt.isPresent() && Boolean.TRUE.equals(codeOpt.get().getIsActive())) {
                discountCode = codeOpt.get();
                if (discountCode.getDiscountType() == com.talentus.scout.domain.enums.DiscountType.PERCENTAGE) {
                    BigDecimal pct = discountCode.getDiscountValue().divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
                    discountAmountUsd = plan.getPriceUsd().multiply(pct).setScale(2, java.math.RoundingMode.HALF_UP);
                } else {
                    discountAmountUsd = discountCode.getDiscountValue().min(plan.getPriceUsd()).setScale(2, java.math.RoundingMode.HALF_UP);
                }
                effectiveAmountUsd = plan.getPriceUsd().subtract(discountAmountUsd).max(BigDecimal.ZERO);
            }
        }

        // Tasa BCV efectiva
        BigDecimal bcvRate = request.getBcvRate();
        if (bcvRate == null || bcvRate.compareTo(BigDecimal.ZERO) <= 0) {
            bcvRate = bcvRateService.getOfficialRate().getRate();
        }

        // Monto en Bolívares efectivo (fallback automático si no fue enviado o es <= 0)
        BigDecimal amountVes = request.getAmountVes();
        if (amountVes == null || amountVes.compareTo(BigDecimal.ZERO) <= 0) {
            amountVes = effectiveAmountUsd.multiply(bcvRate).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        LocalDate pDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();

        PaymentTransaction transaction = PaymentTransaction.builder()
                .user(athlete.getTutor())
                .athlete(athlete)
                .plan(plan)
                .method(PaymentMethod.PAGO_MOVIL)
                .amountUsd(effectiveAmountUsd)
                .amountVes(amountVes)
                .bcvExchangeRate(bcvRate)
                .referenceNumber(request.getReferenceNumber().trim())
                .paymentDate(pDate)
                .originBank(request.getOriginBank())
                .originPhone(request.getOriginPhone())
                .status(PaymentStatus.PENDING_AUDIT)
                .discountCode(discountCode)
                .discountAmountUsd(discountAmountUsd)
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        if (discountCode != null) {
            com.talentus.scout.domain.entity.DiscountCodeRedemption redemption = com.talentus.scout.domain.entity.DiscountCodeRedemption.builder()
                    .discountCode(discountCode)
                    .user(athlete.getTutor())
                    .athlete(athlete)
                    .paymentTransaction(saved)
                    .originalAmountUsd(plan.getPriceUsd())
                    .discountedAmountUsd(discountAmountUsd)
                    .finalAmountUsd(effectiveAmountUsd)
                    .build();
            discountCodeRedemptionRepository.save(redemption);
            discountCode.setCurrentUses(discountCode.getCurrentUses() + 1);
            discountCodeRepository.save(discountCode);
        }
        log.info("Reporte de Pago Móvil registrado con ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Transactional
    public PaymentTransactionResponse reportBinanceManual(UUID tutorId, ReportBinanceManualPaymentRequest request) {
        log.info("Iniciando reporte manual de pago Binance para atleta: {}", request.getAthleteId());

        String ref = request.getReferenceNumber().trim();
        if (paymentTransactionRepository.existsByReferenceNumber(ref)) {
            throw new DuplicatePaymentReferenceException(
                    "El número de referencia de Binance '" + ref + "' ya ha sido registrado previamente.");
        }

        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new IllegalArgumentException("El atleta especificado no existe."));

        if (!athlete.getTutor().getId().equals(tutorId)) {
            throw new AccessDeniedException("No tienes autorización para reportar pagos de este atleta.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("El plan seleccionado no es válido."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("El plan seleccionado no está activo actualmente.");
        }

        // Procesar código de descuento si fue suministrado
        com.talentus.scout.domain.entity.DiscountCode discountCode = null;
        BigDecimal discountAmountUsd = BigDecimal.ZERO;
        BigDecimal effectiveAmountUsd = plan.getPriceUsd();

        if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
            String codeStr = request.getDiscountCode().trim().toUpperCase();
            Optional<com.talentus.scout.domain.entity.DiscountCode> codeOpt = discountCodeRepository.findByCodeIgnoreCase(codeStr);
            if (codeOpt.isPresent() && Boolean.TRUE.equals(codeOpt.get().getIsActive())) {
                discountCode = codeOpt.get();
                if (discountCode.getDiscountType() == com.talentus.scout.domain.enums.DiscountType.PERCENTAGE) {
                    BigDecimal pct = discountCode.getDiscountValue().divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
                    discountAmountUsd = plan.getPriceUsd().multiply(pct).setScale(2, java.math.RoundingMode.HALF_UP);
                } else {
                    discountAmountUsd = discountCode.getDiscountValue().min(plan.getPriceUsd()).setScale(2, java.math.RoundingMode.HALF_UP);
                }
                effectiveAmountUsd = plan.getPriceUsd().subtract(discountAmountUsd).max(BigDecimal.ZERO);
            }
        }

        LocalDate pDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();

        String originInfo = (request.getOriginAccount() != null && !request.getOriginAccount().isBlank())
                ? "Binance (" + request.getOriginAccount().trim() + ")"
                : "Binance Pay / USDT";

        PaymentTransaction transaction = PaymentTransaction.builder()
                .user(athlete.getTutor())
                .athlete(athlete)
                .plan(plan)
                .method(PaymentMethod.BINANCE_PAY)
                .amountUsd(effectiveAmountUsd)
                .amountVes(BigDecimal.ZERO)
                .bcvExchangeRate(BigDecimal.ZERO)
                .referenceNumber(ref)
                .gatewayOrderId(ref)
                .paymentDate(pDate)
                .originBank(originInfo)
                .status(PaymentStatus.PENDING_AUDIT)
                .discountCode(discountCode)
                .discountAmountUsd(discountAmountUsd)
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        if (discountCode != null) {
            com.talentus.scout.domain.entity.DiscountCodeRedemption redemption = com.talentus.scout.domain.entity.DiscountCodeRedemption.builder()
                    .discountCode(discountCode)
                    .user(athlete.getTutor())
                    .athlete(athlete)
                    .paymentTransaction(saved)
                    .originalAmountUsd(plan.getPriceUsd())
                    .discountedAmountUsd(discountAmountUsd)
                    .finalAmountUsd(effectiveAmountUsd)
                    .build();
            discountCodeRedemptionRepository.save(redemption);
            discountCode.setCurrentUses(discountCode.getCurrentUses() + 1);
            discountCodeRepository.save(discountCode);
        }
        log.info("Reporte manual de pago Binance registrado con ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public AthleteSubscriptionStatusResponse getAthleteSubscriptionStatus(UUID athleteId, UUID requesterId, boolean isAdmin) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Atleta no encontrado."));

        if (!isAdmin && (requesterId == null || !athlete.getTutor().getId().equals(requesterId))) {
            throw new AccessDeniedException("No tienes permisos para consultar la suscripción de este atleta.");
        }

        Instant now = Instant.now();
        Optional<AthleteSubscription> activeSub = athleteSubscriptionRepository
                .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athleteId, SubscriptionStatus.ACTIVE);

        if (activeSub.isPresent() && activeSub.get().getEndDate().isAfter(now)) {
            AthleteSubscription sub = activeSub.get();
            long daysRemaining = Math.max(0, Duration.between(now, sub.getEndDate()).toDays());
            return AthleteSubscriptionStatusResponse.builder()
                    .athleteId(athlete.getId())
                    .athleteName(athlete.getFirstName() + " " + athlete.getLastName())
                    .status("ACTIVE")
                    .planId(sub.getPlan().getId())
                    .planName(sub.getPlan().getName())
                    .startDate(sub.getStartDate())
                    .endDate(sub.getEndDate())
                    .daysRemaining(daysRemaining)
                    .canAccessRadar(true)
                    .visibleInScoutDirectory(true)
                    .build();
        }

        // Si no está activa, verificar si tiene un pago en auditoría pendiente
        Optional<PaymentTransaction> pendingTx = paymentTransactionRepository
                .findFirstByAthleteIdAndStatusOrderByCreatedAtDesc(athleteId, PaymentStatus.PENDING_AUDIT);

        if (pendingTx.isPresent()) {
            return AthleteSubscriptionStatusResponse.builder()
                    .athleteId(athlete.getId())
                    .athleteName(athlete.getFirstName() + " " + athlete.getLastName())
                    .status("PENDING_AUDIT")
                    .planId(pendingTx.get().getPlan().getId())
                    .planName(pendingTx.get().getPlan().getName())
                    .canAccessRadar(false)
                    .visibleInScoutDirectory(false)
                    .build();
        }

        return AthleteSubscriptionStatusResponse.builder()
                .athleteId(athlete.getId())
                .athleteName(athlete.getFirstName() + " " + athlete.getLastName())
                .status("INACTIVE")
                .canAccessRadar(false)
                .visibleInScoutDirectory(false)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getTutorPayments(UUID tutorId) {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(tutorId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public PaymentTransactionResponse mapToResponse(PaymentTransaction tx) {
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
