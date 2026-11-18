package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.VexPayProperties;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.PaymentTransaction;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.*;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.service.payment.VexPayService;
import com.talentus.scout.web.dto.CreateVexPaySessionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class VexPayControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private VexPayService vexPayService;

    @Autowired
    private VexPayProperties vexPayProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PaymentGatewaySettingRepository paymentGatewaySettingRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockRestServiceServer mockVexPayServer;
    private User tutorUser;
    private User athleteUser;
    private User adminUser;
    private User scoutUser;
    private Athlete tutorAthlete;
    private Athlete autonomousAthlete;
    private String tutorToken;
    private String athleteToken;
    private String adminToken;
    private String scoutToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(vexPayService, "restTemplate");
        mockVexPayServer = MockRestServiceServer.bindTo(restTemplate).build();

        vexPayProperties.setApiUrl("https://api.vexpay.app");
        vexPayProperties.setApiKey("vk_test_integration_key");
        vexPayProperties.setWebhookSecret("whsec_integration_secret");
        vexPayProperties.setWebhookToleranceSeconds(300L);

        paymentGatewaySettingRepository.findSingleton().ifPresent(s -> {
            s.setVexpayEnabled(true);
            paymentGatewaySettingRepository.save(s);
        });

        if (!subscriptionPlanRepository.existsById("PRO_MONTHLY")) {
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .id("PRO_MONTHLY")
                    .name("Plan Pro Mensual")
                    .priceUsd(new BigDecimal("5.00"))
                    .durationDays(30)
                    .isActive(true)
                    .build());
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        tutorUser = userRepository.save(User.builder()
                .email("tutor." + suffix + "@vexpay-it.com")
                .passwordHash(passwordEncoder.encode("Pass123*"))
                .fullName("Tutor VexPay " + suffix)
                .phoneNumber("+58412" + suffix)
                .role(UserRole.TUTOR)
                .build());
        tutorToken = jwtService.generateToken(tutorUser);

        athleteUser = userRepository.save(User.builder()
                .email("athlete." + suffix + "@vexpay-it.com")
                .passwordHash(passwordEncoder.encode("Pass123*"))
                .fullName("Atleta Mayor " + suffix)
                .phoneNumber("+58424" + suffix)
                .role(UserRole.ATHLETE)
                .build());
        athleteToken = jwtService.generateToken(athleteUser);

        adminUser = userRepository.save(User.builder()
                .email("admin." + suffix + "@vexpay-it.com")
                .passwordHash(passwordEncoder.encode("Pass123*"))
                .fullName("Admin VexPay " + suffix)
                .phoneNumber("+58414" + suffix)
                .role(UserRole.ADMIN)
                .build());
        adminToken = jwtService.generateToken(adminUser);

        scoutUser = userRepository.save(User.builder()
                .email("scout." + suffix + "@vexpay-it.com")
                .passwordHash(passwordEncoder.encode("Pass123*"))
                .fullName("Scout VexPay " + suffix)
                .phoneNumber("+58416" + suffix)
                .role(UserRole.SCOUT)
                .build());
        scoutToken = jwtService.generateToken(scoutUser);

        tutorAthlete = athleteRepository.save(Athlete.builder()
                .tutor(tutorUser)
                .firstName("Lucas")
                .lastName("Rojas")
                .birthDate(LocalDate.of(2010, 3, 15))
                .gender("MALE")
                .primaryPosition("Delantero")
                .preferredFoot("Derecho")
                .nationality("Venezolana")
                .slug("lucas-rojas-" + suffix)
                .isPublic(true)
                .build());

        autonomousAthlete = athleteRepository.save(Athlete.builder()
                .tutor(athleteUser)
                .firstName("Andrés")
                .lastName("Molina")
                .birthDate(LocalDate.of(2004, 1, 10))
                .gender("MALE")
                .primaryPosition("Portero")
                .preferredFoot("Derecho")
                .nationality("Venezolana")
                .slug("andres-molina-" + suffix)
                .isPublic(true)
                .build());
    }

    @Test
    void quoteEndpoint_RolePermissionsAndSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vexpay/quote").param("usdAmount", "5.00")
                        .header("Authorization", "Bearer " + scoutToken))
                .andExpect(status().isForbidden());

        mockVexPayServer.expect(requestTo("https://api.vexpay.app/v1/quote?usdAmount=5.00"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "vk_test_integration_key"))
                .andRespond(withSuccess("""
                        {"usdAmount":5,"bcvRate":915.7476,"rate":915.7476,"rateSource":"BCV","vesAmount":4578.74,"fetchedAt":"2026-11-17T22:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/payments/vexpay/quote").param("usdAmount", "5.00")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateSource", is("BCV")))
                .andExpect(jsonPath("$.vesAmount", is(4578.74)));

        mockVexPayServer.verify();
    }

    @Test
    void fullCheckoutSessionWebhookVerifyAndReversalFlow() throws Exception {
        String sessionId = "cs_it_" + UUID.randomUUID().toString().substring(0, 8);
        mockVexPayServer.expect(requestTo("https://api.vexpay.app/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "vk_test_integration_key"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"id":"%s","object":"checkout.session","status":"open","url":"https://pay.vexpay.app/pay/%s","amountUsd":"5.00","expiresAt":"2026-11-18T00:00:00Z","livemode":false}
                                """.formatted(sessionId, sessionId)));

        CreateVexPaySessionRequest req = CreateVexPaySessionRequest.builder()
                .athleteId(autonomousAthlete.getId())
                .planId("PRO_MONTHLY")
                .build();

        String sessionResp = mockMvc.perform(post("/api/v1/payments/vexpay/checkout-session")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId", is(sessionId)))
                .andExpect(jsonPath("$.checkoutUrl", containsString(sessionId)))
                .andReturn().getResponse().getContentAsString();

        String txId = objectMapper.readTree(sessionResp).get("transactionId").asText();

        // PENDING_GATEWAY NO debe aparecer en la cola manual PENDING_AUDIT ni marcar el atleta como PENDING_AUDIT
        mockMvc.perform(get("/api/v1/admin/payments/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(txId))));

        mockMvc.perform(get("/api/v1/payments/athlete/" + autonomousAthlete.getId() + "/status")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));

        // Webhook con firma inválida debe rechazarse (400 ProblemDetail)
        mockMvc.perform(post("/api/v1/payments/vexpay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("VexPay-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=invalid")
                        .header("VexPay-Event-Id", "evt_bad_" + txId)
                        .content("{\"event\":\"payment.completed\",\"data\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("invalid_signature")));

        // Webhook payment.completed con firma válida activa la suscripción
        String completedPayload = """
                {
                  "event": "payment.completed",
                  "data": {
                    "paymentId": "pay_it_%s",
                    "method": "PAGO_MOVIL",
                    "vesAmount": 4578.74,
                    "rate": 915.7476,
                    "livemode": false,
                    "checkoutSession": { "id": "%s", "reference": "%s" }
                  }
                }
                """.formatted(txId, sessionId, txId);
        String completedSig = sign(Instant.now().getEpochSecond(), completedPayload, vexPayProperties.getWebhookSecret());

        mockMvc.perform(post("/api/v1/payments/vexpay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("VexPay-Signature", completedSig)
                        .header("VexPay-Event-Id", "evt_ok_" + txId)
                        .content(completedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received", is(true)));

        // Verificar que atleta está ACTIVE
        mockMvc.perform(get("/api/v1/payments/athlete/" + autonomousAthlete.getId() + "/status")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Verificar endpoint verify retorna activado
        mockMvc.perform(post("/api/v1/payments/vexpay/sessions/" + sessionId + "/verify")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activated", is(true)))
                .andExpect(jsonPath("$.transactionStatus", is("APPROVED")));

        // Webhook payment.reversed revoca suscripción
        String reversedPayload = """
                {
                  "event": "payment.reversed",
                  "data": {
                    "paymentId": "pay_it_%s",
                    "reversalType": "reversal",
                    "reversalRef": "REV-IT-1",
                    "livemode": false,
                    "checkoutSession": { "id": "%s", "reference": "%s" }
                  }
                }
                """.formatted(txId, sessionId, txId);
        String reversedSig = sign(Instant.now().getEpochSecond(), reversedPayload, vexPayProperties.getWebhookSecret());

        mockMvc.perform(post("/api/v1/payments/vexpay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("VexPay-Signature", reversedSig)
                        .header("VexPay-Event-Id", "evt_rev_" + txId)
                        .content(reversedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received", is(true)));

        mockMvc.perform(get("/api/v1/payments/athlete/" + autonomousAthlete.getId() + "/status")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));

        PaymentTransaction updated = paymentTransactionRepository.findById(UUID.fromString(txId)).orElseThrow();
        assert updated.getStatus() == PaymentStatus.REVERSED;
        assert updated.getMethod() == PaymentMethod.VEXPAY;

        mockVexPayServer.verify();
    }

    @Test
    void reconcileEndpoint_AdminOnly() throws Exception {
        mockMvc.perform(post("/api/v1/admin/payments/vexpay/reconcile")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/payments/vexpay/reconcile")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedCount", notNullValue()));
    }

    @Test
    void checkoutSession_VexPayError_ReturnsProblemDetail() throws Exception {
        mockVexPayServer.expect(requestTo("https://api.vexpay.app/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"payout_account_required\",\"message\":\"Verifica tu cuenta payout\"}"));

        CreateVexPaySessionRequest req = CreateVexPaySessionRequest.builder()
                .athleteId(tutorAthlete.getId())
                .planId("PRO_MONTHLY")
                .build();

        mockMvc.perform(post("/api/v1/payments/vexpay/checkout-session")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("payout_account_required")))
                .andExpect(jsonPath("$.title", is("VexPay Gateway Error")));

        mockVexPayServer.verify();
    }

    private static String sign(long timestamp, String rawBody, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return "t=" + timestamp + ",v1=" + sb;
    }
}
