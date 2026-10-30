package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.CreateSubscriptionPlanRequest;
import com.talentus.scout.web.dto.UpdateSubscriptionPlanRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AdminSubscriptionPlanControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String adminToken;
    private String tutorToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User admin = userRepository.findByEmail("admin.plans@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Admin Plans")
                        .email("admin.plans@talentus.app")
                        .passwordHash(passwordEncoder.encode("AdminPass123*"))
                        .role(UserRole.ADMIN)
                        .phoneNumber("+584140003001")
                        .build()));
        adminToken = jwtService.generateToken(admin);

        User tutor = userRepository.findByEmail("tutor.plans@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Tutor Plans")
                        .email("tutor.plans@talentus.app")
                        .passwordHash(passwordEncoder.encode("TutorPass123*"))
                        .role(UserRole.TUTOR)
                        .phoneNumber("+584140003002")
                        .build()));
        tutorToken = jwtService.generateToken(tutor);

        // Asegurar que al menos existan los planes base para las pruebas
        if (!subscriptionPlanRepository.existsById("PRO_MONTHLY")) {
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .id("PRO_MONTHLY")
                    .name("Plan Pro Mensual")
                    .priceUsd(new BigDecimal("5.00"))
                    .durationDays(30)
                    .maxVideos(5)
                    .hasRadarViews(true)
                    .hasPdfCv(true)
                    .isActive(true)
                    .build());
        }
        if (!subscriptionPlanRepository.existsById("PRO_ANNUAL")) {
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .id("PRO_ANNUAL")
                    .name("Plan Pro Anual")
                    .priceUsd(new BigDecimal("50.00"))
                    .durationDays(365)
                    .maxVideos(10)
                    .hasRadarViews(true)
                    .hasPdfCv(true)
                    .isActive(true)
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        subscriptionPlanRepository.findAll().forEach(plan -> {
            if (plan.getId().startsWith("PRO_TEST_") ||
                plan.getId().startsWith("PRO_TOGGLE_") ||
                plan.getId().startsWith("PRO_UPDATABLE_") ||
                "plan-pro-mensual".equals(plan.getId())) {
                subscriptionPlanRepository.delete(plan);
            }
        });
    }

    @Test
    @DisplayName("Admin puede listar todos los planes de suscripción configurados")
    void getAllPlans_asAdmin_returnsOkAndList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscription-plans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.id == 'PRO_MONTHLY')].priceUsd", hasItem(closeTo(5.00, 0.01))))
                .andExpect(jsonPath("$[?(@.id == 'PRO_ANNUAL')].durationDays", hasItem(365)));
    }

    @Test
    @DisplayName("Admin puede consultar el detalle de un plan específico por ID")
    void getPlanById_asAdmin_returnsPlanDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscription-plans/PRO_MONTHLY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("PRO_MONTHLY")))
                .andExpect(jsonPath("$.name", is("Plan Pro Mensual")))
                .andExpect(jsonPath("$.hasRadarViews", is(true)))
                .andExpect(jsonPath("$.hasPdfCv", is(true)));
    }

    @Test
    @DisplayName("Admin puede crear un nuevo plan de suscripción con parámetros válidos")
    void createPlan_asAdmin_validRequest_createsAndReturns201() throws Exception {
        String uniqueId = "PRO_TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CreateSubscriptionPlanRequest request = CreateSubscriptionPlanRequest.builder()
                .id(uniqueId)
                .name("Plan Promocional Test")
                .priceUsd(new BigDecimal("15.50"))
                .durationDays(90)
                .maxVideos(8)
                .hasRadarViews(true)
                .hasPdfCv(false)
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/subscription-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(uniqueId)))
                .andExpect(jsonPath("$.name", is("Plan Promocional Test")))
                .andExpect(jsonPath("$.priceUsd", closeTo(15.50, 0.01)))
                .andExpect(jsonPath("$.durationDays", is(90)))
                .andExpect(jsonPath("$.maxVideos", is(8)))
                .andExpect(jsonPath("$.hasRadarViews", is(true)))
                .andExpect(jsonPath("$.hasPdfCv", is(false)))
                .andExpect(jsonPath("$.isActive", is(true)));
    }

    @Test
    @DisplayName("Rechazar creación si el identificador del plan ya existe (400 Bad Request)")
    void createPlan_duplicateId_returns400() throws Exception {
        CreateSubscriptionPlanRequest duplicateRequest = CreateSubscriptionPlanRequest.builder()
                .id("PRO_MONTHLY")
                .name("Plan Duplicado")
                .priceUsd(new BigDecimal("9.99"))
                .durationDays(30)
                .maxVideos(5)
                .hasRadarViews(true)
                .hasPdfCv(true)
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/subscription-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Ya existe un plan de suscripción")));
    }

    @Test
    @DisplayName("Rechazar creación con precio negativo o duración menor a 1 día (400 Bad Request)")
    void createPlan_invalidValidation_returns400() throws Exception {
        CreateSubscriptionPlanRequest invalidRequest = CreateSubscriptionPlanRequest.builder()
                .id("INVALID_PLAN")
                .name("")
                .priceUsd(new BigDecimal("-1.00"))
                .durationDays(0)
                .maxVideos(-5)
                .hasRadarViews(true)
                .hasPdfCv(true)
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/subscription-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", notNullValue()));
    }

    @Test
    @DisplayName("Admin puede actualizar precios y parámetros de un plan existente")
    void updatePlan_asAdmin_updatesPriceAndFeatures() throws Exception {
        String testId = "PRO_UPDATABLE_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .id(testId)
                .name("Plan Inicial")
                .priceUsd(new BigDecimal("12.00"))
                .durationDays(60)
                .maxVideos(4)
                .hasRadarViews(false)
                .hasPdfCv(false)
                .isActive(true)
                .build());

        UpdateSubscriptionPlanRequest updateRequest = UpdateSubscriptionPlanRequest.builder()
                .name("Plan Actualizado Premium")
                .priceUsd(new BigDecimal("19.99"))
                .durationDays(60)
                .maxVideos(12)
                .hasRadarViews(true)
                .hasPdfCv(true)
                .isActive(true)
                .build();

        mockMvc.perform(put("/api/v1/admin/subscription-plans/" + testId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testId)))
                .andExpect(jsonPath("$.name", is("Plan Actualizado Premium")))
                .andExpect(jsonPath("$.priceUsd", closeTo(19.99, 0.01)))
                .andExpect(jsonPath("$.maxVideos", is(12)))
                .andExpect(jsonPath("$.hasRadarViews", is(true)))
                .andExpect(jsonPath("$.hasPdfCv", is(true)));
    }

    @Test
    @DisplayName("Admin puede alternar (toggle) el estado activo de un plan")
    void toggleStatus_asAdmin_togglesActiveState() throws Exception {
        String testId = "PRO_TOGGLE_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .id(testId)
                .name("Plan Toggle Test")
                .priceUsd(new BigDecimal("7.00"))
                .durationDays(30)
                .maxVideos(3)
                .hasRadarViews(true)
                .hasPdfCv(false)
                .isActive(true)
                .build());

        // Toggle 1: pasar de activo a inactivo
        mockMvc.perform(patch("/api/v1/admin/subscription-plans/" + testId + "/toggle-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));

        // Toggle 2: pasar de inactivo a activo
        mockMvc.perform(patch("/api/v1/admin/subscription-plans/" + testId + "/toggle-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(true)));
    }

    @Test
    @DisplayName("Bloquear desactivación si es el único plan activo en el sistema (400 Bad Request)")
    void toggleStatus_blocksDeactivatingLastActivePlan() throws Exception {
        // Desactivar temporalmente todos los planes excepto uno
        subscriptionPlanRepository.findAll().forEach(p -> {
            if (!"PRO_MONTHLY".equals(p.getId())) {
                p.setIsActive(false);
                subscriptionPlanRepository.save(p);
            }
        });

        // Intentar desactivar PRO_MONTHLY (único activo)
        mockMvc.perform(patch("/api/v1/admin/subscription-plans/PRO_MONTHLY/toggle-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No es posible desactivar el único plan activo")));

        // Restaurar PRO_ANNUAL a activo para no afectar otros tests
        subscriptionPlanRepository.findById("PRO_ANNUAL").ifPresent(p -> {
            p.setIsActive(true);
            subscriptionPlanRepository.save(p);
        });
    }

    @Test
    @DisplayName("Usuarios sin rol ADMIN reciben 403 Forbidden al intentar acceder a los endpoints")
    void accessDenied_forNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscription-plans")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/subscription-plans"))
                .andExpect(status().isForbidden());
    }
}
