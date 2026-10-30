package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteSubscription;
import com.talentus.scout.domain.entity.PaymentTransaction;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.PaymentTransactionRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.AdminPaymentSummaryResponse;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public AdminPaymentSummaryResponse getMetricsSummary() {
        BigDecimal sumUsd = paymentTransactionRepository.sumApprovedUsd();
        BigDecimal sumVes = paymentTransactionRepository.sumApprovedVes();
        long pending = paymentTransactionRepository.countByStatus(PaymentStatus.PENDING_AUDIT);
        long approved = paymentTransactionRepository.countByStatus(PaymentStatus.APPROVED);
        long rejected = paymentTransactionRepository.countByStatus(PaymentStatus.REJECTED);
        long binanceApproved = paymentTransactionRepository.countByMethodAndStatus(PaymentMethod.BINANCE_PAY, PaymentStatus.APPROVED);
        long pagoMovilApproved = paymentTransactionRepository.countByMethodAndStatus(PaymentMethod.PAGO_MOVIL, PaymentStatus.APPROVED);

        return AdminPaymentSummaryResponse.builder()
                .totalRevenueUsd(sumUsd != null ? sumUsd : BigDecimal.ZERO)
                .totalRevenueVes(sumVes != null ? sumVes : BigDecimal.ZERO)
                .pendingAuditCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .binancePayApprovedCount(binanceApproved)
                .pagoMovilApprovedCount(pagoMovilApproved)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> getPaymentHistory(PaymentMethod method, PaymentStatus status, Pageable pageable) {
        Page<PaymentTransaction> page;
        if (method != null && status != null) {
            page = paymentTransactionRepository.findByMethodAndStatusOrderByCreatedAtDesc(method, status, pageable);
        } else if (method != null) {
            page = paymentTransactionRepository.findByMethodOrderByCreatedAtDesc(method, pageable);
        } else if (status != null) {
            page = paymentTransactionRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = paymentTransactionRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return page.map(paymentService::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getPendingPayments() {
        return paymentTransactionRepository.findByStatusOrderByCreatedAtDesc(PaymentStatus.PENDING_AUDIT).stream()
                .map(paymentService::mapToResponse)
                .toList();
    }

    @Transactional
    public PaymentTransactionResponse approvePayment(UUID transactionId, UUID adminId) {
        log.info("Administrador {} aprobando transacción de pago {}", adminId, transactionId);

        PaymentTransaction tx = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción de pago no encontrada."));

        if (tx.getStatus() != PaymentStatus.PENDING_AUDIT) {
            throw new IllegalStateException("Solo se pueden aprobar transacciones en estado de auditoría pendiente.");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario administrador no encontrado."));

        Instant now = Instant.now();
        tx.setStatus(PaymentStatus.APPROVED);
        tx.setAuditedByAdmin(admin);
        tx.setAuditedAt(now);

        Athlete athlete = tx.getAthlete();
        SubscriptionPlan plan = tx.getPlan();

        // Lógica de cálculo o extensión de vigencia
        Optional<AthleteSubscription> existingActive = athleteSubscriptionRepository
                .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE);

        if (existingActive.isPresent() && existingActive.get().getEndDate().isAfter(now)) {
            AthleteSubscription activeSub = existingActive.get();
            Instant extendedEnd = activeSub.getEndDate().plus(Duration.ofDays(plan.getDurationDays()));
            activeSub.setEndDate(extendedEnd);
            activeSub.setPlan(plan);
            athleteSubscriptionRepository.save(activeSub);
            log.info("Suscripción existente extendida hasta {} para atleta {}", extendedEnd, athlete.getId());
        } else {
            AthleteSubscription newSub = AthleteSubscription.builder()
                    .athlete(athlete)
                    .plan(plan)
                    .startDate(now)
                    .endDate(now.plus(Duration.ofDays(plan.getDurationDays())))
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            athleteSubscriptionRepository.save(newSub);
            log.info("Nueva suscripción Pro activada hasta {} para atleta {}", newSub.getEndDate(), athlete.getId());
        }

        PaymentTransaction saved = paymentTransactionRepository.save(tx);

        // Notificación por correo al tutor
        try {
            if (tx.getUser() != null && tx.getUser().getEmail() != null) {
                emailService.sendPaymentStatusNotification(
                        tx.getUser().getEmail(),
                        tx.getUser().getFullName(),
                        athlete.getFirstName() + " " + athlete.getLastName(),
                        plan.getName(),
                        true,
                        null
                );
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de correo al tutor: {}", e.getMessage());
        }

        return paymentService.mapToResponse(saved);
    }

    @Transactional
    public PaymentTransactionResponse rejectPayment(UUID transactionId, UUID adminId, String reason) {
        log.info("Administrador {} rechazando transacción de pago {}. Motivo: {}", adminId, transactionId, reason);

        PaymentTransaction tx = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción de pago no encontrada."));

        if (tx.getStatus() != PaymentStatus.PENDING_AUDIT) {
            throw new IllegalStateException("Solo se pueden rechazar transacciones en estado de auditoría pendiente.");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario administrador no encontrado."));

        Instant now = Instant.now();
        tx.setStatus(PaymentStatus.REJECTED);
        tx.setAuditedByAdmin(admin);
        tx.setAuditedAt(now);
        tx.setRejectionReason(reason != null ? reason.trim() : "Comprobante o referencia no coincide.");

        PaymentTransaction saved = paymentTransactionRepository.save(tx);

        // Notificación por correo al tutor
        try {
            if (tx.getUser() != null && tx.getUser().getEmail() != null) {
                emailService.sendPaymentStatusNotification(
                        tx.getUser().getEmail(),
                        tx.getUser().getFullName(),
                        tx.getAthlete().getFirstName() + " " + tx.getAthlete().getLastName(),
                        tx.getPlan().getName(),
                        false,
                        tx.getRejectionReason()
                );
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de correo al tutor: {}", e.getMessage());
        }

        return paymentService.mapToResponse(saved);
    }
}
