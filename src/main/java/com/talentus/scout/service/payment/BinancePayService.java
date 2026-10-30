package com.talentus.scout.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.BinanceWebhookResponse;
import com.talentus.scout.web.dto.CreateBinanceOrderRequest;
import com.talentus.scout.web.dto.CreateBinanceOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinancePayService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AthleteRepository athleteRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${talentus.payments.binance-pay.api-key:talentus_binance_mock_key}")
    private String apiKey;

    @Value("${talentus.payments.binance-pay.secret-key:talentus_binance_mock_secret}")
    private String secretKey;

    @Value("${talentus.payments.binance-pay.merchant-id:talentus_merchant_001}")
    private String merchantId;

    @Value("${talentus.payments.binance-pay.sandbox:true}")
    private boolean sandbox;

    @Transactional
    public CreateBinanceOrderResponse createOrder(UUID tutorId, CreateBinanceOrderRequest request) {
        log.info("Iniciando creación de orden Binance Pay para atleta {} por tutor {}", request.getAthleteId(), tutorId);

        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new IllegalArgumentException("El atleta especificado no existe."));

        if (!athlete.getTutor().getId().equals(tutorId)) {
            throw new AccessDeniedException("No tienes autorización para gestionar pagos de este atleta.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("El plan seleccionado no es válido."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("El plan seleccionado no está activo actualmente.");
        }

        String merchantTradeNo = "TS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String prepayId = "BINANCE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentTransaction transaction = PaymentTransaction.builder()
                .user(athlete.getTutor())
                .athlete(athlete)
                .plan(plan)
                .method(PaymentMethod.BINANCE_PAY)
                .amountUsd(plan.getPriceUsd())
                .referenceNumber(merchantTradeNo)
                .gatewayOrderId(merchantTradeNo)
                .paymentDate(LocalDate.now())
                .status(PaymentStatus.PENDING_AUDIT)
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        log.info("Transacción Binance Pay registrada con ID: {} y TradeNo: {}", saved.getId(), merchantTradeNo);

        long expireTime = Instant.now().plus(Duration.ofMinutes(15)).toEpochMilli();
        String checkoutUrl = sandbox
                ? "http://localhost:8080/api/v1/payments/binance-pay/mock-checkout/" + merchantTradeNo
                : "https://pay.binance.com/checkout?order=" + prepayId;

        return CreateBinanceOrderResponse.builder()
                .transactionId(saved.getId())
                .prepayId(prepayId)
                .gatewayOrderId(merchantTradeNo)
                .checkoutUrl(checkoutUrl)
                .qrContent("binancepay://pay?tradeNo=" + merchantTradeNo)
                .expireTime(expireTime)
                .amountUsd(plan.getPriceUsd())
                .currency("USDT")
                .build();
    }

    @Transactional
    public BinanceWebhookResponse handleWebhook(String payload, String signature, String timestamp, String nonce) {
        log.info("Recibido webhook de Binance Pay. Timestamp: {}, Nonce: {}", timestamp, nonce);

        if (!verifySignature(payload, signature, timestamp, nonce)) {
            log.warn("Firma del webhook de Binance Pay inválida.");
            return BinanceWebhookResponse.builder()
                    .returnCode("FAIL")
                    .returnMessage("Invalid cryptographic signature")
                    .build();
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String bizStatus = root.path("bizStatus").asText("");
            String bizId = root.path("bizId").asText("");

            if (bizId.isBlank()) {
                bizId = root.path("data").path("merchantTradeNo").asText("");
            }
            if (bizStatus.isBlank()) {
                bizStatus = root.path("data").path("status").asText("");
            }

            log.info("Procesando evento webhook para orden: {}, estado: {}", bizId, bizStatus);

            Optional<PaymentTransaction> txOpt = paymentTransactionRepository.findByGatewayOrderId(bizId);
            if (txOpt.isEmpty()) {
                txOpt = paymentTransactionRepository.findByGatewayOrderId(bizId.trim());
            }

            if (txOpt.isEmpty()) {
                log.warn("No se encontró transacción para el TradeNo: {}", bizId);
                return BinanceWebhookResponse.builder()
                        .returnCode("FAIL")
                        .returnMessage("Transaction not found for merchantTradeNo: " + bizId)
                        .build();
            }

            PaymentTransaction tx = txOpt.get();

            if ("PAY_SUCCESS".equalsIgnoreCase(bizStatus) || "SUCCESS".equalsIgnoreCase(bizStatus)) {
                if (tx.getStatus() == PaymentStatus.APPROVED) {
                    log.info("La transacción {} ya fue aprobada previamente (Idempotencia).", tx.getId());
                    return BinanceWebhookResponse.builder()
                            .returnCode("SUCCESS")
                            .returnMessage("Already processed")
                            .build();
                }

                Instant now = Instant.now();
                tx.setStatus(PaymentStatus.APPROVED);
                tx.setAuditedAt(now);
                tx.setRawWebhookPayload(payload);

                Athlete athlete = tx.getAthlete();
                SubscriptionPlan plan = tx.getPlan();

                Optional<AthleteSubscription> existingActive = athleteSubscriptionRepository
                        .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE);

                if (existingActive.isPresent() && existingActive.get().getEndDate().isAfter(now)) {
                    AthleteSubscription activeSub = existingActive.get();
                    Instant extendedEnd = activeSub.getEndDate().plus(Duration.ofDays(plan.getDurationDays()));
                    activeSub.setEndDate(extendedEnd);
                    activeSub.setPlan(plan);
                    athleteSubscriptionRepository.save(activeSub);
                    log.info("Suscripción existente extendida por Binance Pay hasta {} para atleta {}", extendedEnd, athlete.getId());
                } else {
                    AthleteSubscription newSub = AthleteSubscription.builder()
                            .athlete(athlete)
                            .plan(plan)
                            .startDate(now)
                            .endDate(now.plus(Duration.ofDays(plan.getDurationDays())))
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    athleteSubscriptionRepository.save(newSub);
                    log.info("Nueva suscripción Pro activada automáticamente por Binance Pay hasta {} para atleta {}", newSub.getEndDate(), athlete.getId());
                }

                paymentTransactionRepository.save(tx);

                // Notificar al tutor por correo
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
                    log.warn("No se pudo enviar correo de confirmación de Binance Pay: {}", e.getMessage());
                }

                return BinanceWebhookResponse.builder()
                        .returnCode("SUCCESS")
                        .returnMessage("Payment processed and subscription activated successfully")
                        .build();
            } else {
                log.info("Webhook recibido con estado no exitoso: {}. Marcando transacción como REJECTED.", bizStatus);
                tx.setStatus(PaymentStatus.REJECTED);
                tx.setAuditedAt(Instant.now());
                tx.setRejectionReason("Binance Pay status: " + bizStatus);
                tx.setRawWebhookPayload(payload);
                paymentTransactionRepository.save(tx);

                return BinanceWebhookResponse.builder()
                        .returnCode("SUCCESS")
                        .returnMessage("Status recorded: " + bizStatus)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error al procesar payload del webhook de Binance Pay: {}", e.getMessage(), e);
            return BinanceWebhookResponse.builder()
                    .returnCode("FAIL")
                    .returnMessage("Error processing payload: " + e.getMessage())
                    .build();
        }
    }

    public boolean verifySignature(String payload, String signature, String timestamp, String nonce) {
        if (sandbox && ("mock-valid-sig".equals(signature) || signature == null || signature.isBlank())) {
            return true;
        }

        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String payloadToSign = (timestamp != null ? timestamp : "") + "\n"
                    + (nonce != null ? nonce : "") + "\n"
                    + (payload != null ? payload : "") + "\n";

            Mac sha512Hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512Hmac.init(keySpec);

            byte[] macData = sha512Hmac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : macData) {
                result.append(String.format("%02x", b));
            }

            return result.toString().equalsIgnoreCase(signature) || (sandbox && signature.startsWith("mock-"));
        } catch (Exception e) {
            log.error("Error al validar HMAC-SHA512 de Binance Pay: {}", e.getMessage());
            return false;
        }
    }
}
