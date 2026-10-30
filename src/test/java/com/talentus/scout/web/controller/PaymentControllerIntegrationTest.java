package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.*;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.ReportPagoMovilRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PaymentControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private AthleteSubscriptionRepository athleteSubscriptionRepository;

    @Autowired
    private DiscountCodeRepository discountCodeRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private User tutorUser;
    private User adminUser;
    private Athlete testAthlete;
    private String tutorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        tutorUser = User.builder()
                .email("tutor." + suffix + "@payment-test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Tutor Pagos " + suffix)
                .phoneNumber("+58412" + suffix)
                .role(UserRole.TUTOR)
                .build();
        tutorUser = userRepository.save(tutorUser);
        tutorToken = jwtService.generateToken(tutorUser);

        adminUser = User.builder()
                .email("admin." + suffix + "@payment-test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Admin Pagos " + suffix)
                .phoneNumber("+58414" + suffix)
                .role(UserRole.ADMIN)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        testAthlete = Athlete.builder()
                .tutor(tutorUser)
                .firstName("Samuel")
                .lastName("Rivas")
                .birthDate(LocalDate.of(2011, 4, 10))
                .gender("MALE")
                .primaryPosition("Mediocampista Central")
                .preferredFoot("Derecho")
                .nationality("Venezolana")
                .slug("samuel-rivas-" + suffix)
                .isPublic(true)
                .build();
        testAthlete = athleteRepository.save(testAthlete);

        // Ensure PRO_MONTHLY exists
        if (!subscriptionPlanRepository.existsById("PRO_MONTHLY")) {
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .id("PRO_MONTHLY")
                    .name("Plan Pro Mensual")
                    .priceUsd(new BigDecimal("5.00"))
                    .durationDays(30)
                    .isActive(true)
                    .build());
        }
    }

    @Test
    void getPaymentConfig_PublicAccess_Returns200AndConfig() throws Exception {
        mockMvc.perform(get("/api/v1/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bcvRate", notNullValue()))
                .andExpect(jsonPath("$.bankName", notNullValue()))
                .andExpect(jsonPath("$.plans", not(empty())));
    }

    @Test
    void getBcvRate_PublicAccess_Returns200AndRate() throws Exception {
        mockMvc.perform(get("/api/v1/payments/bcv-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate", notNullValue()))
                .andExpect(jsonPath("$.source", notNullValue()));
    }

    @Test
    void reportPagoMovilAndAdminApproval_CompleteWorkflow() throws Exception {
        // 1. Tutor reporta pago móvil
        ReportPagoMovilRequest request = ReportPagoMovilRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber("889900")
                .paymentDate(LocalDate.now())
                .originBank("Banesco")
                .originPhone("04121234567")
                .amountVes(new BigDecimal("227.50"))
                .bcvRate(new BigDecimal("45.50"))
                .build();

        String txId = mockMvc.perform(post("/api/v1/payments/report-pago-movil")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING_AUDIT")))
                .andExpect(jsonPath("$.referenceNumber", is("889900")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID transactionId = UUID.fromString(objectMapper.readTree(txId).get("id").asText());

        // 2. Admin consulta pagos pendientes
        mockMvc.perform(get("/api/v1/admin/payments/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(transactionId.toString())));

        // 3. Admin aprueba el pago
        mockMvc.perform(post("/api/v1/admin/payments/" + transactionId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        // 4. Tutor consulta estado de suscripción de su atleta: debe estar ACTIVE
        mockMvc.perform(get("/api/v1/payments/athlete/" + testAthlete.getId() + "/status")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.visibleInScoutDirectory", is(true)));
    }

    @Test
    void reportPagoMovil_WithoutAmountVes_SuccessCalculatesAutomatically() throws Exception {
        ReportPagoMovilRequest request = ReportPagoMovilRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber("776655")
                .paymentDate(LocalDate.now())
                .originBank("Banesco")
                .originPhone("04149876543")
                .build();

        mockMvc.perform(post("/api/v1/payments/report-pago-movil")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING_AUDIT")))
                .andExpect(jsonPath("$.referenceNumber", is("776655")))
                .andExpect(jsonPath("$.amountVes", notNullValue()))
                .andExpect(jsonPath("$.amountUsd", is(5.00)));
    }

    @Test
    void binancePay_CreateOrderAndWebhook_SuccessActivatesPro() throws Exception {
        // 1. Tutor crea orden Binance Pay
        String orderReqJson = "{\"athleteId\":\"" + testAthlete.getId() + "\",\"planId\":\"PRO_MONTHLY\"}";

        String orderResp = mockMvc.perform(post("/api/v1/payments/binance-pay/create-order")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderReqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gatewayOrderId", notNullValue()))
                .andExpect(jsonPath("$.currency", is("USDT")))
                .andExpect(jsonPath("$.amountUsd", is(5.00)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tradeNo = objectMapper.readTree(orderResp).get("gatewayOrderId").asText();

        // 2. Webhook de confirmación enviado por Binance Pay
        String webhookPayload = "{\"bizType\":\"PAY\",\"bizId\":\"" + tradeNo + "\",\"bizStatus\":\"PAY_SUCCESS\"}";

        mockMvc.perform(post("/api/v1/payments/binance-pay/mock-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("BinancePay-Signature", "mock-valid-sig")
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnCode", is("SUCCESS")));

        // 3. Verificar que atleta ahora está PRO ACTIVO
        mockMvc.perform(get("/api/v1/payments/athlete/" + testAthlete.getId() + "/status")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.planName", is("Plan Pro Mensual")));
    }

    @Test
    void adminPayments_GetSummaryAndHistory_Success() throws Exception {
        // Consultar métricas del panel admin
        mockMvc.perform(get("/api/v1/admin/payments/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenueUsd", notNullValue()))
                .andExpect(jsonPath("$.totalRevenueVes", notNullValue()))
                .andExpect(jsonPath("$.approvedCount", notNullValue()));

        // Consultar historial paginado
        mockMvc.perform(get("/api/v1/admin/payments/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    void validateCoupon_Endpoint_Success() throws Exception {
        String testCode = "VAL" + System.currentTimeMillis() % 10000;
        discountCodeRepository.save(DiscountCode.builder()
                .code(testCode)
                .discountType(com.talentus.scout.domain.enums.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .isActive(true)
                .build());

        String payload = "{\"code\":\"" + testCode + "\",\"athleteId\":\"" + testAthlete.getId() + "\",\"planId\":\"PRO_MONTHLY\"}";

        mockMvc.perform(post("/api/v1/payments/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.code", is(testCode.toUpperCase())))
                .andExpect(jsonPath("$.discountAmountUsd", is(1.0)))
                .andExpect(jsonPath("$.finalAmountUsd", is(4.0)));
    }

    @Test
    void redeemFreePromo_Endpoint_Success() throws Exception {
        String testCode = "BECA" + System.currentTimeMillis() % 10000;
        discountCodeRepository.save(DiscountCode.builder()
                .code(testCode)
                .discountType(com.talentus.scout.domain.enums.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(100))
                .isActive(true)
                .maxUses(10)
                .oneUsePerAthlete(true)
                .build());

        String payload = "{\"code\":\"" + testCode + "\",\"athleteId\":\"" + testAthlete.getId() + "\",\"planId\":\"PRO_MONTHLY\"}";

        mockMvc.perform(post("/api/v1/payments/coupons/redeem-free")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method", is("DISCOUNT_CODE")))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.amountUsd").value(0));

        // Verificar que la suscripción del atleta fue activada automáticamente
        mockMvc.perform(get("/api/v1/payments/athlete/" + testAthlete.getId() + "/status")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void getPaymentConfig_IncludesBinanceProps() throws Exception {
        mockMvc.perform(get("/api/v1/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binancePayId", notNullValue()))
                .andExpect(jsonPath("$.binanceWalletAddress", notNullValue()))
                .andExpect(jsonPath("$.binanceNetwork", notNullValue()));
    }

    @Test
    void reportBinanceManual_Success() throws Exception {
        String uniqueRef = "BINANCE-REF-" + System.currentTimeMillis();
        com.talentus.scout.web.dto.ReportBinanceManualPaymentRequest req = com.talentus.scout.web.dto.ReportBinanceManualPaymentRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber(uniqueRef)
                .paymentDate(LocalDate.now())
                .originAccount("TutorCryptoNick")
                .build();

        mockMvc.perform(post("/api/v1/payments/binance-pay/manual-report")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method", is("BINANCE_PAY")))
                .andExpect(jsonPath("$.status", is("PENDING_AUDIT")))
                .andExpect(jsonPath("$.referenceNumber", is(uniqueRef)))
                .andExpect(jsonPath("$.amountUsd", is(5.0)));
    }

    @Test
    void reportBinanceManual_DuplicateReference_Returns409() throws Exception {
        String uniqueRef = "DUP-BINANCE-" + System.currentTimeMillis();
        com.talentus.scout.web.dto.ReportBinanceManualPaymentRequest req = com.talentus.scout.web.dto.ReportBinanceManualPaymentRequest.builder()
                .athleteId(testAthlete.getId())
                .planId("PRO_MONTHLY")
                .referenceNumber(uniqueRef)
                .paymentDate(LocalDate.now())
                .build();

        // Primer reporte exitoso
        mockMvc.perform(post("/api/v1/payments/binance-pay/manual-report")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Segundo reporte con misma referencia -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/payments/binance-pay/manual-report")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")));
    }
}
