package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.PaymentGatewaySettingRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.UpdatePaymentGatewaySettingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminPaymentGatewayConfigControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentGatewaySettingRepository paymentGatewaySettingRepository;

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

        User admin = userRepository.findByEmail("admin.gateway@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Admin Gateway")
                        .email("admin.gateway@talentus.app")
                        .passwordHash(passwordEncoder.encode("AdminPass123*"))
                        .role(UserRole.ADMIN)
                        .phoneNumber("+584140001001")
                        .build()));
        adminToken = jwtService.generateToken(admin);

        User tutor = userRepository.findByEmail("tutor.gateway@talentus.app")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Tutor Gateway")
                        .email("tutor.gateway@talentus.app")
                        .passwordHash(passwordEncoder.encode("TutorPass123*"))
                        .role(UserRole.TUTOR)
                        .phoneNumber("+584140001002")
                        .build()));
        tutorToken = jwtService.generateToken(tutor);
    }

    @Test
    @DisplayName("Admin puede consultar la configuración de pasarelas de pago")
    void getGatewaySettings_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments/gateway-settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagoMovilBank", notNullValue()))
                .andExpect(jsonPath("$.pagoMovilPhone", notNullValue()))
                .andExpect(jsonPath("$.pagoMovilEnabled", notNullValue()))
                .andExpect(jsonPath("$.binanceManualEnabled", notNullValue()));
    }

    @Test
    @DisplayName("Usuario no admin recibe 403 Forbidden al consultar configuración de pasarelas")
    void getGatewaySettings_asTutor_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments/gateway-settings")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede actualizar configuración de Pago Móvil y Binance")
    void updateGatewaySettings_asAdmin_success() throws Exception {
        UpdatePaymentGatewaySettingsRequest request = UpdatePaymentGatewaySettingsRequest.builder()
                .pagoMovilEnabled(false)
                .pagoMovilBank("Banco Mercantil")
                .pagoMovilPhone("04249876543")
                .pagoMovilIdNumber("J-99887766-5")
                .pagoMovilAccountHolder("Talentus Producciones C.A.")
                .binanceManualEnabled(true)
                .binancePayId("998877665")
                .binanceWalletAddress("TXabcdef1234567890walletAddressUSDT")
                .binanceNetwork("USDT (TRC-20)")
                .build();

        mockMvc.perform(put("/api/v1/admin/payments/gateway-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagoMovilEnabled", is(false)))
                .andExpect(jsonPath("$.pagoMovilBank", is("Banco Mercantil")))
                .andExpect(jsonPath("$.pagoMovilPhone", is("04249876543")))
                .andExpect(jsonPath("$.binancePayId", is("998877665")));

        // Verificar que el endpoint público de tutores /api/v1/payments/config refleja el cambio en tiempo real
        mockMvc.perform(get("/api/v1/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagoMovilEnabled", is(false)))
                .andExpect(jsonPath("$.bankName", is("Banco Mercantil")))
                .andExpect(jsonPath("$.phoneNumber", is("04249876543")))
                .andExpect(jsonPath("$.binancePayId", is("998877665")));
    }

    @Test
    @DisplayName("Validación rechaza solicitud con campos obligatorios vacíos")
    void updateGatewaySettings_validationError_badRequest() throws Exception {
        UpdatePaymentGatewaySettingsRequest invalidRequest = UpdatePaymentGatewaySettingsRequest.builder()
                .pagoMovilEnabled(true)
                .pagoMovilBank("") // Blank
                .pagoMovilPhone("")
                .pagoMovilIdNumber("")
                .pagoMovilAccountHolder("")
                .binanceManualEnabled(true)
                .build();

        mockMvc.perform(put("/api/v1/admin/payments/gateway-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin puede subir código QR de Binance exitosamente")
    void uploadBinanceQr_asAdmin_success() throws Exception {
        MockMultipartFile mockQr = new MockMultipartFile(
                "file",
                "test-binance-qr.png",
                "image/png",
                "FAKE_QR_IMAGE_BYTES_12345".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/admin/payments/gateway-settings/binance-qr")
                        .file(mockQr)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrImageUrl", containsString("/uploads/payments/")));
    }

    @Test
    @DisplayName("Subida de QR rechaza archivos no admitidos (ej. texto o pdf)")
    void uploadBinanceQr_invalidType_badRequest() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "No soy una imagen".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/admin/payments/gateway-settings/binance-qr")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Solo se admiten imágenes")));
    }
}
