package com.talentus.scout.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.VexPayProperties;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.exception.VexPayApiException;
import com.talentus.scout.repository.*;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class VexPayService {

    private final VexPayProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VexPayWebhookEventRepository webhookEventRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final PaymentGatewaySettingRepository paymentGatewaySettingRepository;
    private final DiscountCodeService discountCodeService;
    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountCodeRedemptionRepository discountCodeRedemptionRepository;
    private final EmailService emailService;

    @Autowired
    public VexPayService(
            VexPayProperties properties,
            ObjectMapper objectMapper,
            PaymentTransactionRepository paymentTransactionRepository,
            VexPayWebhookEventRepository webhookEventRepository,
            AthleteSubscriptionRepository athleteSubscriptionRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            AthleteRepository athleteRepository,
            UserRepository userRepository,
            PaymentGatewaySettingRepository paymentGatewaySettingRepository,
            DiscountCodeService discountCodeService,
            DiscountCodeRepository discountCodeRepository,
            DiscountCodeRedemptionRepository discountCodeRedemptionRepository,
            EmailService emailService
    ) {
        this(
                properties,
                createDefaultRestTemplate(),
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
    }

    public VexPayService(
            VexPayProperties properties,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            PaymentTransactionRepository paymentTransactionRepository,
            VexPayWebhookEventRepository webhookEventRepository,
            AthleteSubscriptionRepository athleteSubscriptionRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            AthleteRepository athleteRepository,
            UserRepository userRepository,
            PaymentGatewaySettingRepository paymentGatewaySettingRepository,
            DiscountCodeService discountCodeService,
            DiscountCodeRepository discountCodeRepository,
            DiscountCodeRedemptionRepository discountCodeRedemptionRepository,
            EmailService emailService
    ) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.athleteSubscriptionRepository = athleteSubscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.athleteRepository = athleteRepository;
        this.userRepository = userRepository;
        this.paymentGatewaySettingRepository = paymentGatewaySettingRepository;
        this.discountCodeService = discountCodeService;
        this.discountCodeRepository = discountCodeRepository;
        this.discountCodeRedemptionRepository = discountCodeRedemptionRepository;
        this.emailService = emailService;
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Transactional(readOnly = true)
    public VexPayQuoteResponse getQuote(BigDecimal usdAmount, String planId, String discountCode, UUID athleteId) {
        ensureVexPayEnabledAndConfigured();

        BigDecimal originalAmountUsd = null;
        BigDecimal discountAmountUsd = BigDecimal.ZERO;
        String appliedCode = null;
        BigDecimal targetUsd = usdAmount;

        if (planId != null && !planId.isBlank()) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(planId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("El plan seleccionado no es válido."));
            originalAmountUsd = plan.getPriceUsd().setScale(2, RoundingMode.HALF_UP);
            targetUsd = originalAmountUsd;

            if (discountCode != null && !discountCode.isBlank()) {
                ValidateCouponResponse coupon = discountCodeService.validateCoupon(discountCode.trim(), athleteId, plan.getId());
                if (coupon.isValid()) {
                    targetUsd = coupon.getFinalAmountUsd() != null
                            ? coupon.getFinalAmountUsd().setScale(2, RoundingMode.HALF_UP)
                            : targetUsd;
                    discountAmountUsd = coupon.getDiscountAmountUsd() != null
                            ? coupon.getDiscountAmountUsd().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    appliedCode = coupon.getCode();
                } else {
                    throw new IllegalArgumentException(coupon.getMessage());
                }
            }
        }

        if (targetUsd == null) {
            throw new IllegalArgumentException("Debes indicar usdAmount o planId para consultar la cotización en VexPay.");
        }

        if (targetUsd.compareTo(BigDecimal.ZERO) == 0) {
            return VexPayQuoteResponse.builder()
                    .usdAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .originalAmountUsd(originalAmountUsd)
                    .discountAmountUsd(discountAmountUsd)
                    .discountCode(appliedCode)
                    .rate(BigDecimal.ZERO)
                    .rateSource("BCV")
                    .bcvRate(BigDecimal.ZERO)
                    .vesAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .fetchedAt(Instant.now())
                    .build();
        }

        if (targetUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto USD debe ser mayor a cero.");
        }

        String url = normalizeBaseUrl() + "/v1/quote?usdAmount=" + targetUsd.setScale(2, RoundingMode.HALF_UP).toPlainString();
        JsonNode quoteNode = executeVexPayRequest(HttpMethod.GET, url, null, null);

        return VexPayQuoteResponse.builder()
                .usdAmount(decimalField(quoteNode, "usdAmount", targetUsd))
                .originalAmountUsd(originalAmountUsd != null ? originalAmountUsd : targetUsd)
                .discountAmountUsd(discountAmountUsd)
                .discountCode(appliedCode)
                .rate(decimalField(quoteNode, "rate", null))
                .rateSource(quoteNode.path("rateSource").asText("BCV"))
                .bcvRate(decimalField(quoteNode, "bcvRate", null))
                .vesAmount(decimalField(quoteNode, "vesAmount", null))
                .medianRate(decimalField(quoteNode, "medianRate", null))
                .medianVesAmount(decimalField(quoteNode, "medianVesAmount", null))
                .fetchedAt(parseInstant(quoteNode.path("fetchedAt").asText(null)))
                .build();
    }

    @Transactional
    public CreateVexPaySessionResponse createCheckoutSession(UUID requesterId, boolean isAdmin, CreateVexPaySessionRequest request) {
        ensureVexPayEnabledAndConfigured();

        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new IllegalArgumentException("El atleta especificado no existe."));

        if (!isAdmin && (athlete.getTutor() == null || !athlete.getTutor().getId().equals(requesterId))) {
            throw new AccessDeniedException("No tienes autorización para gestionar pagos de este atleta.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("El plan seleccionado no es válido."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("El plan seleccionado no está activo actualmente.");
        }

        DiscountCode discountCodeEntity = null;
        BigDecimal discountAmountUsd = BigDecimal.ZERO;
        BigDecimal effectiveAmountUsd = plan.getPriceUsd().setScale(2, RoundingMode.HALF_UP);

        if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
            ValidateCouponResponse validation = discountCodeService.validateCoupon(
                    request.getDiscountCode().trim(),
                    athlete.getId(),
                    plan.getId()
            );
            if (!validation.isValid()) {
                throw new IllegalArgumentException(validation.getMessage());
            }
            discountAmountUsd = validation.getDiscountAmountUsd() != null
                    ? validation.getDiscountAmountUsd().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            effectiveAmountUsd = validation.getFinalAmountUsd() != null
                    ? validation.getFinalAmountUsd().setScale(2, RoundingMode.HALF_UP)
                    : effectiveAmountUsd;

            if (effectiveAmountUsd.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El cupón cubre el 100% del plan. Activa la membresía directamente mediante redención gratuita.");
            }

            discountCodeEntity = discountCodeRepository.findByCodeIgnoreCase(validation.getCode())
                    .orElse(null);
        }

        User ownerUser = athlete.getTutor();
        if (ownerUser == null && requesterId != null) {
            ownerUser = userRepository.findById(requesterId).orElse(null);
        }
        if (ownerUser == null) {
            throw new IllegalStateException("No se pudo determinar el usuario responsable de la transacción.");
        }

        String tempRef = "VP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        PaymentTransaction tx = PaymentTransaction.builder()
                .user(ownerUser)
                .athlete(athlete)
                .plan(plan)
                .method(PaymentMethod.VEXPAY)
                .amountUsd(effectiveAmountUsd)
                .referenceNumber(tempRef)
                .paymentDate(LocalDate.now())
                .status(PaymentStatus.PENDING_GATEWAY)
                .discountCode(discountCodeEntity)
                .discountAmountUsd(discountAmountUsd)
                .build();

        tx = paymentTransactionRepository.save(tx);
        String txIdStr = tx.getId().toString();
        String normalizedRef = "VP-" + txIdStr.replace("-", "").substring(0, 16).toUpperCase();
        tx.setReferenceNumber(normalizedRef);

        String successUrl = appendQueryParam(properties.getSuccessUrl(), "txId", txIdStr);
        String cancelUrl = appendQueryParam(properties.getCancelUrl(), "txId", txIdStr);

        String description = plan.getName() + " — " + athlete.getFirstName() + " " + athlete.getLastName();
        if (description.length() > 200) {
            description = description.substring(0, 200);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amountUsd", effectiveAmountUsd);
        payload.put("description", description);
        payload.put("reference", txIdStr);
        payload.put("successUrl", successUrl);
        payload.put("cancelUrl", cancelUrl);
        // Omitimos 'methods' intencionalmente para ofrecer todos los canales habilitados en VexPay (Pago Móvil, C2P, USDT/USDC, COP)
        payload.put("metadata", Map.of(
                "transactionId", txIdStr,
                "athleteId", athlete.getId().toString(),
                "planId", plan.getId()
        ));

        String url = normalizeBaseUrl() + "/v1/checkout/sessions";
        JsonNode sessionNode = executeVexPayRequest(
                HttpMethod.POST,
                url,
                payload,
                "vexpay-session-" + txIdStr
        );

        String sessionId = sessionNode.path("id").asText("");
        String checkoutUrl = sessionNode.path("url").asText("");
        String sessionStatus = sessionNode.path("status").asText("open");
        boolean livemode = sessionNode.path("livemode").asBoolean(properties.isEffectiveLivemode());
        Instant expiresAt = parseInstant(sessionNode.path("expiresAt").asText(null));

        if (sessionId.isBlank() || checkoutUrl.isBlank()) {
            throw new VexPayApiException(HttpStatus.BAD_GATEWAY, "invalid_gateway_response",
                    "VexPay no devolvió un identificador o URL de sesión válidos.");
        }

        tx.setVexpaySessionId(sessionId);
        tx.setGatewayOrderId(sessionId);
        paymentTransactionRepository.save(tx);

        log.info("Sesión VexPay creada: sessionId={}, txId={}, amountUsd={}, livemode={}",
                sessionId, tx.getId(), effectiveAmountUsd, livemode);

        return CreateVexPaySessionResponse.builder()
                .checkoutUrl(checkoutUrl)
                .sessionId(sessionId)
                .transactionId(tx.getId())
                .amountUsd(effectiveAmountUsd)
                .expiresAt(expiresAt)
                .livemode(livemode)
                .status(sessionStatus)
                .build();
    }

    @Transactional
    public Map<String, Object> handleWebhook(String rawBody, String signatureHeader, String eventId) {
        verifyWebhookSignature(rawBody, signatureHeader);

        if (eventId != null && !eventId.isBlank() && webhookEventRepository.existsByEventId(eventId.trim())) {
            log.info("Webhook VexPay duplicado ignorado por idempotencia. eventId={}", eventId);
            return Map.of("received", true, "deduplicated", true);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new VexPayApiException(HttpStatus.BAD_REQUEST, "invalid_json", "El payload del webhook no es un JSON válido.");
        }

        String eventType = root.path("event").asText("");
        JsonNode data = root.path("data");
        boolean livemode = data.path("livemode").asBoolean(false);

        if (eventId != null && !eventId.isBlank()) {
            try {
                VexPayWebhookEvent eventRecord = VexPayWebhookEvent.builder()
                        .eventId(eventId.trim())
                        .eventType(eventType.isBlank() ? "unknown" : eventType)
                        .livemode(livemode)
                        .payload(rawBody)
                        .build();
                webhookEventRepository.saveAndFlush(eventRecord);
            } catch (DataIntegrityViolationException dup) {
                log.info("Webhook VexPay concurrente ya registrado (idempotente). eventId={}", eventId);
                return Map.of("received", true, "deduplicated", true);
            }
        }

        if ("notification.test".equalsIgnoreCase(eventType)) {
            log.info("Webhook de prueba VexPay recibido correctamente. eventId={}", eventId);
            return Map.of("received", true, "event", eventType);
        }

        Optional<PaymentTransaction> txOpt = resolveTransactionFromWebhookData(data);
        if (txOpt.isEmpty()) {
            log.warn("Webhook VexPay {} recibido sin transacción local asociada. eventId={}", eventType, eventId);
            return Map.of("received", true, "matched", false);
        }

        PaymentTransaction tx = txOpt.get();
        String paymentId = textOrNull(data.path("paymentId"));
        String subMethod = textOrNull(data.path("method"));

        switch (eventType) {
            case "payment.pending" -> {
                if (paymentId != null) {
                    tx.setVexpayPaymentId(paymentId);
                }
                if (subMethod != null) {
                    tx.setVexpaySubMethod(subMethod);
                }
                tx.setRawWebhookPayload(rawBody);
                paymentTransactionRepository.save(tx);
                log.info("VexPay payment.pending registrado para tx={}", tx.getId());
            }
            case "payment.completed" -> {
                BigDecimal vesAmount = decimalField(data, "vesAmount", null);
                BigDecimal rate = decimalField(data, "rate", decimalField(data, "bcvRate", null));
                applyPaymentCompleted(tx, paymentId, subMethod, vesAmount, rate, rawBody);
            }
            case "payment.failed", "payment.canceled" -> {
                if (tx.getStatus() == PaymentStatus.PENDING_GATEWAY) {
                    String failureCode = textOrNull(data.path("failureCode"));
                    String cancelReason = textOrNull(data.path("cancelReason"));
                    String detail = failureCode != null ? failureCode : (cancelReason != null ? cancelReason : eventType);
                    tx.setStatus(PaymentStatus.REJECTED);
                    tx.setAuditedAt(Instant.now());
                    tx.setRejectionReason("VexPay " + eventType + ": " + detail);
                    if (paymentId != null) {
                        tx.setVexpayPaymentId(paymentId);
                    }
                    if (subMethod != null) {
                        tx.setVexpaySubMethod(subMethod);
                    }
                    tx.setRawWebhookPayload(rawBody);
                    paymentTransactionRepository.save(tx);
                    log.info("Transacción VexPay {} marcada como REJECTED ({})", tx.getId(), detail);
                }
            }
            case "payment.reversed" -> {
                String reversalType = textOrNull(data.path("reversalType"));
                String reversalRef = textOrNull(data.path("reversalRef"));
                applyPaymentReversed(tx, paymentId, reversalType, reversalRef, rawBody);
            }
            default -> log.info("Evento VexPay no manejado explícitamente: {}", eventType);
        }

        return Map.of("received", true, "matched", true, "event", eventType);
    }

    @Transactional
    public VexPaySessionVerifyResponse verifyCheckoutSession(String sessionOrTxId, UUID requesterId, boolean isAdmin) {
        PaymentTransaction tx = resolveTransactionByIdentifier(sessionOrTxId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró ninguna transacción VexPay asociada."));

        if (!isAdmin && (tx.getAthlete().getTutor() == null || !tx.getAthlete().getTutor().getId().equals(requesterId))) {
            throw new AccessDeniedException("No tienes permisos para consultar esta sesión de pago.");
        }

        if (tx.getMethod() != PaymentMethod.VEXPAY) {
            throw new IllegalArgumentException("La transacción indicada no corresponde a VexPay.");
        }

        if (tx.getStatus() == PaymentStatus.APPROVED) {
            return buildVerifyResponse(tx, "paid", true, "¡Pago confirmado! La membresía Pro está activa.");
        }
        if (tx.getStatus() == PaymentStatus.REVERSED) {
            return buildVerifyResponse(tx, "reversed", false, "El pago fue reversado por la pasarela bancaria.");
        }

        String sessionId = tx.getVexpaySessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return buildVerifyResponse(tx, "open", false, "Tu pago sigue en verificación bancaria.");
        }

        if (!properties.isConfigured()) {
            return buildVerifyResponse(tx, "open", false, "Esperando confirmación automática de la pasarela.");
        }

        JsonNode sessionNode = fetchCheckoutSession(sessionId);
        return syncTransactionWithSessionNode(tx, sessionNode);
    }

    @Transactional
    public VexPayReconciliationResponse reconcilePendingTransactions() {
        Instant executedAt = Instant.now();
        if (!properties.isConfigured()) {
            log.debug("Conciliación VexPay omitida: VEXPAY_API_KEY no configurada.");
            return VexPayReconciliationResponse.builder()
                    .checkedCount(0)
                    .approvedCount(0)
                    .rejectedCount(0)
                    .stillPendingCount(0)
                    .errorCount(0)
                    .executedAt(executedAt)
                    .build();
        }

        Instant lookbackStart = executedAt.minus(Duration.ofHours(Math.max(1, properties.getReconcileLookbackHours())));
        List<PaymentTransaction> pendingList = paymentTransactionRepository
                .findByMethodAndStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                        PaymentMethod.VEXPAY,
                        PaymentStatus.PENDING_GATEWAY,
                        lookbackStart
                );

        int checked = 0;
        int approved = 0;
        int rejected = 0;
        int stillPending = 0;
        int errors = 0;

        for (PaymentTransaction tx : pendingList) {
            checked++;
            try {
                if (tx.getVexpaySessionId() != null && !tx.getVexpaySessionId().isBlank()) {
                    JsonNode sessionNode = fetchCheckoutSession(tx.getVexpaySessionId());
                    VexPaySessionVerifyResponse result = syncTransactionWithSessionNode(tx, sessionNode);
                    if (result.getTransactionStatus() == PaymentStatus.APPROVED) {
                        approved++;
                    } else if (result.getTransactionStatus() == PaymentStatus.REJECTED) {
                        rejected++;
                    } else {
                        stillPending++;
                    }
                } else {
                    JsonNode paymentNode = fetchPaymentByRef(tx.getId().toString());
                    if (paymentNode != null && "COMPLETED".equalsIgnoreCase(paymentNode.path("status").asText(""))) {
                        applyPaymentCompleted(
                                tx,
                                textOrNull(paymentNode.path("paymentId")),
                                textOrNull(paymentNode.path("method")),
                                decimalField(paymentNode, "vesAmount", null),
                                decimalField(paymentNode, "rate", decimalField(paymentNode, "bcvRate", null)),
                                paymentNode.toString()
                        );
                        approved++;
                    } else {
                        stillPending++;
                    }
                }
            } catch (Exception ex) {
                errors++;
                log.warn("Error conciliando transacción VexPay {}: {}", tx.getId(), ex.getMessage());
            }
        }

        log.info("Conciliación VexPay finalizada: checked={}, approved={}, rejected={}, pending={}, errors={}",
                checked, approved, rejected, stillPending, errors);

        return VexPayReconciliationResponse.builder()
                .checkedCount(checked)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .stillPendingCount(stillPending)
                .errorCount(errors)
                .executedAt(executedAt)
                .build();
    }

    public void verifyWebhookSignature(String rawBody, String signatureHeader) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new VexPayApiException(HttpStatus.UNAUTHORIZED, "webhook_secret_not_configured",
                    "El secreto de webhook VexPay no está configurado en el servidor.");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new VexPayApiException(HttpStatus.BAD_REQUEST, "missing_signature",
                    "Falta el encabezado VexPay-Signature.");
        }
        if (rawBody == null) {
            rawBody = "";
        }

        String timestampStr = null;
        List<String> v1Signatures = new ArrayList<>();

        for (String part : signatureHeader.split(",")) {
            String trimmed = part.trim();
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx <= 0) continue;
            String key = trimmed.substring(0, eqIdx).trim();
            String val = trimmed.substring(eqIdx + 1).trim();
            if ("t".equals(key)) {
                timestampStr = val;
            } else if ("v1".equals(key)) {
                v1Signatures.add(val);
            }
        }

        if (timestampStr == null || timestampStr.isBlank() || v1Signatures.isEmpty()) {
            throw new VexPayApiException(HttpStatus.BAD_REQUEST, "invalid_signature_format",
                    "Formato de encabezado VexPay-Signature inválido.");
        }

        long timestampSec;
        try {
            timestampSec = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new VexPayApiException(HttpStatus.BAD_REQUEST, "invalid_signature_timestamp",
                    "Marca temporal inválida en VexPay-Signature.");
        }

        long nowSec = Instant.now().getEpochSecond();
        long tolerance = Math.max(1L, properties.getWebhookToleranceSeconds());
        if (Math.abs(nowSec - timestampSec) > tolerance) {
            throw new VexPayApiException(HttpStatus.BAD_REQUEST, "signature_expired",
                    "La firma del webhook VexPay excede la tolerancia temporal permitida.");
        }

        String signedPayload = timestampStr + "." + rawBody;
        String expectedHex = computeHmacSha256Hex(signedPayload, secret);
        byte[] expectedBytes = expectedHex.getBytes(StandardCharsets.UTF_8);

        boolean matched = v1Signatures.stream().anyMatch(sig ->
                MessageDigest.isEqual(expectedBytes, sig.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
        );

        if (!matched) {
            throw new VexPayApiException(HttpStatus.BAD_REQUEST, "invalid_signature",
                    "La firma criptográfica VexPay-Signature no coincide.");
        }
    }

    private VexPaySessionVerifyResponse syncTransactionWithSessionNode(PaymentTransaction tx, JsonNode sessionNode) {
        String sessionStatus = sessionNode.path("status").asText("open");
        String paymentId = textOrNull(sessionNode.path("paymentId"));
        Instant expiresAt = parseInstant(sessionNode.path("expiresAt").asText(null));

        if ("paid".equalsIgnoreCase(sessionStatus)) {
            String subMethod = null;
            BigDecimal vesAmount = decimalField(sessionNode, "amountVes", null);
            BigDecimal rate = null;

            if (paymentId != null) {
                JsonNode paymentNode = fetchPaymentByIdSafe(paymentId);
                if (paymentNode != null) {
                    subMethod = textOrNull(paymentNode.path("method"));
                    vesAmount = decimalField(paymentNode, "vesAmount", vesAmount);
                    rate = decimalField(paymentNode, "rate", decimalField(paymentNode, "bcvRate", null));
                }
            }

            applyPaymentCompleted(tx, paymentId, subMethod, vesAmount, rate, sessionNode.toString());
            return buildVerifyResponse(tx, sessionStatus, true, "¡Pago confirmado con éxito! Tu Plan Pro ya está activo.");
        }

        if ("expired".equalsIgnoreCase(sessionStatus) || "canceled".equalsIgnoreCase(sessionStatus)) {
            if (tx.getStatus() == PaymentStatus.PENDING_GATEWAY) {
                tx.setStatus(PaymentStatus.REJECTED);
                tx.setAuditedAt(Instant.now());
                tx.setRejectionReason("Sesión VexPay " + sessionStatus);
                tx.setRawWebhookPayload(sessionNode.toString());
                paymentTransactionRepository.save(tx);
            }
            return buildVerifyResponse(tx, sessionStatus, false,
                    "expired".equalsIgnoreCase(sessionStatus)
                            ? "La sesión de pago expiró antes de completarse."
                            : "La sesión de pago fue cancelada.");
        }

        if ("failed".equalsIgnoreCase(sessionStatus) && expiresAt != null && expiresAt.isBefore(Instant.now())) {
            if (tx.getStatus() == PaymentStatus.PENDING_GATEWAY) {
                tx.setStatus(PaymentStatus.REJECTED);
                tx.setAuditedAt(Instant.now());
                tx.setRejectionReason("Sesión VexPay fallida y expirada");
                tx.setRawWebhookPayload(sessionNode.toString());
                paymentTransactionRepository.save(tx);
            }
            return buildVerifyResponse(tx, sessionStatus, false, "El intento de pago falló y la sesión expiró.");
        }

        return buildVerifyResponse(tx, sessionStatus, false,
                "Tu pago está en verificación bancaria. Si ya transferiste, pulsa 'Verificar nuevamente' en unos segundos.");
    }

    private void applyPaymentCompleted(
            PaymentTransaction tx,
            String paymentId,
            String subMethod,
            BigDecimal vesAmount,
            BigDecimal rate,
            String rawPayload
    ) {
        if (tx.getStatus() == PaymentStatus.APPROVED || tx.getStatus() == PaymentStatus.REVERSED) {
            log.info("Transacción VexPay {} ya estaba en estado {} (Idempotencia).", tx.getId(), tx.getStatus());
            return;
        }

        Instant now = Instant.now();
        if (paymentId != null && !paymentId.isBlank()) {
            tx.setVexpayPaymentId(paymentId);
        }
        if (subMethod != null && !subMethod.isBlank()) {
            tx.setVexpaySubMethod(subMethod.toUpperCase(Locale.ROOT));
        }
        if (vesAmount != null) {
            tx.setAmountVes(vesAmount);
        }
        if (rate != null) {
            tx.setBcvExchangeRate(rate);
        }
        tx.setStatus(PaymentStatus.APPROVED);
        tx.setAuditedAt(now);
        tx.setRejectionReason(null);
        tx.setRawWebhookPayload(rawPayload);

        Athlete athlete = tx.getAthlete();
        SubscriptionPlan plan = tx.getPlan();

        Optional<AthleteSubscription> existingActive = athleteSubscriptionRepository
                .findFirstByAthleteIdAndStatusOrderByEndDateDesc(athlete.getId(), SubscriptionStatus.ACTIVE);

        if (existingActive.isPresent() && existingActive.get().getEndDate().isAfter(now)) {
            AthleteSubscription activeSub = existingActive.get();
            Instant previousEnd = activeSub.getEndDate();
            Instant extendedEnd = previousEnd.plus(Duration.ofDays(plan.getDurationDays()));
            activeSub.setEndDate(extendedEnd);
            activeSub.setPlan(plan);
            AthleteSubscription savedSub = athleteSubscriptionRepository.save(activeSub);

            tx.setAppliedSubscriptionId(savedSub.getId());
            tx.setPreviousSubscriptionEndDate(previousEnd);
            log.info("Suscripción existente {} extendida por VexPay hasta {} para atleta {}",
                    savedSub.getId(), extendedEnd, athlete.getId());
        } else {
            AthleteSubscription newSub = AthleteSubscription.builder()
                    .athlete(athlete)
                    .plan(plan)
                    .startDate(now)
                    .endDate(now.plus(Duration.ofDays(plan.getDurationDays())))
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            AthleteSubscription savedSub = athleteSubscriptionRepository.save(newSub);

            tx.setAppliedSubscriptionId(savedSub.getId());
            tx.setPreviousSubscriptionEndDate(null);
            log.info("Nueva suscripción Pro {} activada por VexPay hasta {} para atleta {}",
                    savedSub.getId(), savedSub.getEndDate(), athlete.getId());
        }

        // Registrar redención del cupón únicamente cuando el pago se confirma
        if (tx.getDiscountCode() != null && !discountCodeRedemptionRepository.existsByPaymentTransactionId(tx.getId())) {
            DiscountCode discountCode = tx.getDiscountCode();
            DiscountCodeRedemption redemption = DiscountCodeRedemption.builder()
                    .discountCode(discountCode)
                    .user(tx.getUser())
                    .athlete(athlete)
                    .paymentTransaction(tx)
                    .originalAmountUsd(plan.getPriceUsd())
                    .discountedAmountUsd(tx.getDiscountAmountUsd() != null ? tx.getDiscountAmountUsd() : BigDecimal.ZERO)
                    .finalAmountUsd(tx.getAmountUsd())
                    .build();
            discountCodeRedemptionRepository.save(redemption);
            discountCode.setCurrentUses(discountCode.getCurrentUses() + 1);
            discountCodeRepository.save(discountCode);
        }

        paymentTransactionRepository.save(tx);

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
            log.warn("No se pudo enviar notificación de correo por activación VexPay: {}", e.getMessage());
        }
    }

    private void applyPaymentReversed(
            PaymentTransaction tx,
            String paymentId,
            String reversalType,
            String reversalRef,
            String rawPayload
    ) {
        if (tx.getStatus() == PaymentStatus.REVERSED) {
            log.info("Transacción VexPay {} ya estaba REVERSED (Idempotencia).", tx.getId());
            return;
        }

        Instant now = Instant.now();
        if (paymentId != null && !paymentId.isBlank()) {
            tx.setVexpayPaymentId(paymentId);
        }
        tx.setStatus(PaymentStatus.REVERSED);
        tx.setAuditedAt(now);
        tx.setRawWebhookPayload(rawPayload);
        tx.setRejectionReason(String.format("Pago reversado (%s%s)",
                reversalType != null ? reversalType : "reversal",
                reversalRef != null ? " - Ref: " + reversalRef : ""));

        // Revocar o restaurar la suscripción vinculada
        if (tx.getAppliedSubscriptionId() != null) {
            athleteSubscriptionRepository.findById(tx.getAppliedSubscriptionId()).ifPresent(sub -> {
                if (tx.getPreviousSubscriptionEndDate() != null) {
                    sub.setEndDate(tx.getPreviousSubscriptionEndDate());
                    if (!sub.getEndDate().isAfter(now)) {
                        sub.setStatus(SubscriptionStatus.CANCELLED);
                    }
                    log.info("Suscripción {} restaurada a fecha previa {} por reverso VexPay", sub.getId(), sub.getEndDate());
                } else {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    sub.setEndDate(now);
                    log.info("Suscripción {} cancelada automáticamente por reverso VexPay", sub.getId());
                }
                athleteSubscriptionRepository.save(sub);
            });
        } else {
            athleteSubscriptionRepository
                    .findFirstByAthleteIdAndStatusOrderByEndDateDesc(tx.getAthlete().getId(), SubscriptionStatus.ACTIVE)
                    .ifPresent(sub -> {
                        sub.setStatus(SubscriptionStatus.CANCELLED);
                        sub.setEndDate(now);
                        athleteSubscriptionRepository.save(sub);
                        log.info("Suscripción activa {} del atleta {} cancelada por reverso VexPay", sub.getId(), tx.getAthlete().getId());
                    });
        }

        paymentTransactionRepository.save(tx);

        try {
            if (tx.getUser() != null && tx.getUser().getEmail() != null) {
                emailService.sendPaymentReversedNotification(
                        tx.getUser().getEmail(),
                        tx.getUser().getFullName(),
                        tx.getAthlete().getFirstName() + " " + tx.getAthlete().getLastName(),
                        tx.getPlan().getName(),
                        reversalType,
                        reversalRef
                );
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de notificación por reverso VexPay: {}", e.getMessage());
        }
    }

    private Optional<PaymentTransaction> resolveTransactionFromWebhookData(JsonNode data) {
        JsonNode checkoutSession = data.path("checkoutSession");
        String sessionId = textOrNull(checkoutSession.path("id"));
        if (sessionId != null) {
            Optional<PaymentTransaction> bySession = paymentTransactionRepository.findByVexpaySessionId(sessionId);
            if (bySession.isPresent()) return bySession;
        }

        String reference = textOrNull(checkoutSession.path("reference"));
        if (reference == null) {
            reference = textOrNull(checkoutSession.path("metadata").path("transactionId"));
        }
        if (reference == null) {
            reference = textOrNull(data.path("externalRef"));
        }
        if (reference != null) {
            Optional<PaymentTransaction> byIdOrRef = resolveTransactionByIdentifier(reference);
            if (byIdOrRef.isPresent()) return byIdOrRef;
        }

        String paymentId = textOrNull(data.path("paymentId"));
        if (paymentId != null) {
            return paymentTransactionRepository.findByVexpayPaymentId(paymentId);
        }

        return Optional.empty();
    }

    private Optional<PaymentTransaction> resolveTransactionByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        Optional<PaymentTransaction> bySession = paymentTransactionRepository.findByVexpaySessionId(trimmed);
        if (bySession.isPresent()) {
            return bySession;
        }
        try {
            UUID uuid = UUID.fromString(trimmed);
            Optional<PaymentTransaction> byId = paymentTransactionRepository.findById(uuid);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return paymentTransactionRepository.findByReferenceNumber(trimmed);
    }

    private JsonNode fetchCheckoutSession(String sessionId) {
        String url = normalizeBaseUrl() + "/v1/checkout/sessions/" + sessionId;
        return executeVexPayRequest(HttpMethod.GET, url, null, null);
    }

    private JsonNode fetchPaymentByIdSafe(String paymentId) {
        try {
            String url = normalizeBaseUrl() + "/v1/payments/" + paymentId;
            return executeVexPayRequest(HttpMethod.GET, url, null, null);
        } catch (Exception e) {
            log.debug("No se pudo obtener detalle de pago VexPay {}: {}", paymentId, e.getMessage());
            return null;
        }
    }

    private JsonNode fetchPaymentByRef(String externalRef) {
        try {
            String url = normalizeBaseUrl() + "/v1/payments/by-ref/" + externalRef;
            return executeVexPayRequest(HttpMethod.GET, url, null, null);
        } catch (VexPayApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private JsonNode executeVexPayRequest(HttpMethod method, String url, Object body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            if (response.getBody() == null || response.getBody().isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw mapVexPayHttpException(ex);
        } catch (ResourceAccessException ex) {
            log.error("Error de conectividad con VexPay API ({}): {}", url, ex.getMessage());
            throw new VexPayApiException(HttpStatus.SERVICE_UNAVAILABLE, "vexpay_unreachable",
                    "No se pudo conectar con la pasarela VexPay en este momento. Intenta de nuevo en unos segundos.", ex);
        } catch (VexPayApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado comunicándose con VexPay: {}", ex.getMessage(), ex);
            throw new VexPayApiException(HttpStatus.BAD_GATEWAY, "vexpay_unexpected_error",
                    "Error procesando respuesta de la pasarela VexPay.", ex);
        }
    }

    private VexPayApiException mapVexPayHttpException(HttpStatusCodeException ex) {
        int rawCode = ex.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(rawCode);
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String body = ex.getResponseBodyAsString();
        String errorCode = "vexpay_http_" + rawCode;
        String detailMessage = "Error en la pasarela VexPay (HTTP " + rawCode + ").";

        try {
            if (body != null && !body.isBlank()) {
                JsonNode errNode = objectMapper.readTree(body);
                if (errNode.hasNonNull("error")) {
                    errorCode = errNode.get("error").asText(errorCode);
                }
                if (errNode.has("message")) {
                    JsonNode msgNode = errNode.get("message");
                    if (msgNode.isArray() && !msgNode.isEmpty()) {
                        detailMessage = msgNode.get(0).asText(detailMessage);
                    } else if (msgNode.isTextual()) {
                        detailMessage = msgNode.asText(detailMessage);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (rawCode == 401) {
            detailMessage = "Credenciales de VexPay inválidas o inactivas en el servidor.";
        } else if (rawCode == 403 && "live_mode_not_activated".equalsIgnoreCase(errorCode)) {
            detailMessage = "La cuenta Live de VexPay aún está pendiente de activación comercial.";
        } else if (rawCode == 409 && "payout_account_required".equalsIgnoreCase(errorCode)) {
            detailMessage = "La cuenta comercial en VexPay requiere una cuenta de liquidación verificada para operar en vivo.";
        } else if (rawCode == 503 && "rate_unavailable".equalsIgnoreCase(errorCode)) {
            detailMessage = "VexPay no dispone temporalmente de la tasa de cambio solicitada. Intenta nuevamente en unos momentos.";
        }

        log.warn("Error de API VexPay status={}, errorCode={}, detail={}", rawCode, errorCode, detailMessage);
        return new VexPayApiException(status, errorCode, detailMessage, ex);
    }

    private void ensureVexPayEnabledAndConfigured() {
        boolean enabled = paymentGatewaySettingRepository.findSingleton()
                .map(s -> s.getVexpayEnabled() != null ? s.getVexpayEnabled() : true)
                .orElse(true);

        if (!enabled) {
            throw new IllegalStateException("La pasarela VexPay se encuentra deshabilitada temporalmente por administración.");
        }
        if (!properties.isConfigured()) {
            throw new VexPayApiException(HttpStatus.SERVICE_UNAVAILABLE, "vexpay_not_configured",
                    "La pasarela VexPay no cuenta con API Key configurada en el servidor.");
        }
    }

    private VexPaySessionVerifyResponse buildVerifyResponse(
            PaymentTransaction tx,
            String sessionStatus,
            boolean activated,
            String message
    ) {
        return VexPaySessionVerifyResponse.builder()
                .transactionId(tx.getId())
                .sessionId(tx.getVexpaySessionId())
                .sessionStatus(sessionStatus)
                .transactionStatus(tx.getStatus())
                .paymentId(tx.getVexpayPaymentId())
                .subMethod(tx.getVexpaySubMethod())
                .athleteId(tx.getAthlete().getId())
                .athleteName(tx.getAthlete().getFirstName() + " " + tx.getAthlete().getLastName())
                .planName(tx.getPlan().getName())
                .activated(activated)
                .message(message)
                .build();
    }

    private String normalizeBaseUrl() {
        String base = properties.getApiUrl();
        if (base == null || base.isBlank()) {
            return "https://api.vexpay.app";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String appendQueryParam(String baseUrl, String key, String value) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + key + "=" + value;
    }

    private String computeHmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Error al calcular firma HMAC-SHA256", e);
        }
    }

    private BigDecimal decimalField(JsonNode node, String fieldName, BigDecimal fallback) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return fallback;
        }
        JsonNode f = node.get(fieldName);
        if (f.isNumber()) {
            return f.decimalValue();
        }
        if (f.isTextual()) {
            try {
                return new BigDecimal(f.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String val = node.asText("").trim();
        return val.isEmpty() ? null : val;
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
