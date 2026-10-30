package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.CompleteProfileRequest;
import com.talentus.scout.web.dto.SocialAuthRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthControllerSocialIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/social/google: Nuevo usuario con token mock debe responder 200 con needsProfileCompletion = true")
    void testGoogleAuthEndpoint() throws Exception {
        SocialAuthRequest request = SocialAuthRequest.builder()
                .token("mock-google-token:integracion.google@talentusscout.com")
                .mockName("Integracion Google")
                .build();

        mockMvc.perform(post("/api/v1/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("integracion.google@talentusscout.com"))
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.needsProfileCompletion").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/social/microsoft: Nuevo usuario con token mock debe responder 200 con needsProfileCompletion = true")
    void testMicrosoftAuthEndpoint() throws Exception {
        SocialAuthRequest request = SocialAuthRequest.builder()
                .token("mock-ms-token:integracion.ms@talentusscout.com")
                .mockName("Integracion Microsoft")
                .build();

        mockMvc.perform(post("/api/v1/auth/social/microsoft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("integracion.ms@talentusscout.com"))
                .andExpect(jsonPath("$.provider").value("MICROSOFT"))
                .andExpect(jsonPath("$.needsProfileCompletion").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/social/complete-profile: Tutor autenticado completa perfil exitosamente")
    void testCompleteProfileEndpoint() throws Exception {
        // 1. Iniciar sesión social para obtener token
        SocialAuthRequest socialReq = SocialAuthRequest.builder()
                .token("mock-google-token:tutor.completa@talentusscout.com")
                .mockName("Tutor Completa")
                .build();

        String responseBody = mockMvc.perform(post("/api/v1/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(responseBody, "$.token");

        // 2. Completar perfil con rol TUTOR y consentimiento
        CompleteProfileRequest completeReq = CompleteProfileRequest.builder()
                .role(UserRole.TUTOR)
                .phoneNumber("+584141112233")
                .acceptLegalConsent(true)
                .build();

        mockMvc.perform(post("/api/v1/auth/social/complete-profile")
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", "JUnit-Test-Runner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TUTOR"))
                .andExpect(jsonPath("$.email").value("tutor.completa@talentusscout.com"))
                .andExpect(jsonPath("$.token").isString());
    }
}
