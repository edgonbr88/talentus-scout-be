package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.DiscountCode;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.DiscountType;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.DiscountCodeRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.CreateDiscountCodeRequest;
import com.talentus.scout.web.dto.UpdateDiscountCodeRequest;
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
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminDiscountCodeControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiscountCodeRepository discountCodeRepository;

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

        User admin = userRepository.findByEmail("admin.coupons@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Admin Cupones")
                        .email("admin.coupons@talentus.app")
                        .passwordHash(passwordEncoder.encode("AdminPass123*"))
                        .role(UserRole.ADMIN)
                        .phoneNumber("+584140000001")
                        .build()));
        adminToken = jwtService.generateToken(admin);

        User tutor = userRepository.findByEmail("tutor.coupons@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Tutor Cupones")
                        .email("tutor.coupons@talentus.app")
                        .passwordHash(passwordEncoder.encode("TutorPass123*"))
                        .role(UserRole.TUTOR)
                        .phoneNumber("+584140000002")
                        .build()));
        tutorToken = jwtService.generateToken(tutor);
    }

    @Test
    @DisplayName("Admin puede listar códigos de descuento paginados")
    void getAllCodes_asAdmin_success() throws Exception {
        discountCodeRepository.save(DiscountCode.builder()
                .code("CODE-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .description("Campaña Test")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/v1/admin/discount-codes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    @DisplayName("Usuario no admin (TUTOR) recibe 403 Forbidden al intentar acceder a la gestión de códigos")
    void getAllCodes_asTutor_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/discount-codes")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede crear un nuevo cupón con validación de unicidad")
    void createCode_asAdmin_success() throws Exception {
        String testCode = "SUMMER" + System.currentTimeMillis() % 10000;
        CreateDiscountCodeRequest request = CreateDiscountCodeRequest.builder()
                .code(testCode)
                .description("Promoción de Verano 2026")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(25.00))
                .maxUses(50)
                .oneUsePerAthlete(true)
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/discount-codes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(testCode.toUpperCase())))
                .andExpect(jsonPath("$.discountValue", is(25.00)))
                .andExpect(jsonPath("$.isActive", is(true)));
    }

    @Test
    @DisplayName("Admin puede alternar (toggle) el estado activo/inactivo de un código")
    void toggleStatus_asAdmin_success() throws Exception {
        DiscountCode code = discountCodeRepository.save(DiscountCode.builder()
                .code("TOGGLE" + System.currentTimeMillis() % 10000)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(2.50))
                .isActive(true)
                .build());

        mockMvc.perform(patch("/api/v1/admin/discount-codes/" + code.getId() + "/toggle-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));
    }

    @Test
    @DisplayName("Admin puede actualizar la descripción y el valor de un cupón existente")
    void updateCode_asAdmin_success() throws Exception {
        DiscountCode code = discountCodeRepository.save(DiscountCode.builder()
                .code("UPDATE" + System.currentTimeMillis() % 10000)
                .description("Inicial")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .isActive(true)
                .build());

        UpdateDiscountCodeRequest request = UpdateDiscountCodeRequest.builder()
                .description("Descripción Modificada")
                .discountValue(BigDecimal.valueOf(30))
                .build();

        mockMvc.perform(put("/api/v1/admin/discount-codes/" + code.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Descripción Modificada")))
                .andExpect(jsonPath("$.discountValue").value(30));
    }

    @Test
    @DisplayName("Admin puede eliminar un código de descuento")
    void deleteCode_asAdmin_success() throws Exception {
        DiscountCode code = discountCodeRepository.save(DiscountCode.builder()
                .code("DELETE" + System.currentTimeMillis() % 10000)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1.00))
                .isActive(true)
                .build());

        mockMvc.perform(delete("/api/v1/admin/discount-codes/" + code.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
