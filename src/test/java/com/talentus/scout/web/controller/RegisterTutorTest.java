package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.RegisterTutorRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RegisterTutorTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TutorLegalConsentRepository tutorLegalConsentRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void successfulTutorRegistrationShouldPersistUserAndConsentAndReturn201WithJwt() throws Exception {
        String testEmail = "tutor.test." + System.currentTimeMillis() + "@talentus.app";
        RegisterTutorRequest request = new RegisterTutorRequest(
                "Mariana Gómez",
                testEmail,
                "PasswordSeguro2026*",
                "+584149876543",
                true
        );

        String responseBody = mockMvc.perform(post("/api/v1/auth/register-tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")
                        .header("X-Forwarded-For", "190.202.45.12")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.role").value("TUTOR"))
                .andExpect(jsonPath("$.fullName").value("Mariana Gómez"))
                .andReturn().getResponse().getContentAsString();

        // Verificar persistencia en base de datos
        Optional<User> savedUserOpt = userRepository.findByEmail(testEmail);
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals(UserRole.TUTOR, savedUser.getRole());
        assertEquals("+584149876543", savedUser.getPhoneNumber());

        // Verificar consentimiento legal LOPNNA
        List<TutorLegalConsent> consents = tutorLegalConsentRepository.findByTutorId(savedUser.getId());
        assertEquals(1, consents.size());
        TutorLegalConsent consent = consents.get(0);
        assertTrue(consent.isAcceptedLopnnaTerms());
        assertEquals("1.0", consent.getTermsVersion());
        assertEquals("190.202.45.12", consent.getIpAddress());
        assertTrue(consent.getUserAgent().contains("iPhone"));
        assertNotNull(consent.getAcceptedAt());
    }

    @Test
    void registrationWithLopnnaFalseShouldFailWith400BadRequest() throws Exception {
        String testEmail = "lopnna.false." + System.currentTimeMillis() + "@talentus.app";
        RegisterTutorRequest request = new RegisterTutorRequest(
                "Pedro Infante",
                testEmail,
                "PasswordSeguro2026*",
                "+584121112233",
                false // Rechaza LOPNNA
        );

        mockMvc.perform(post("/api/v1/auth/register-tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.acceptLopnnaTerms").exists());

        assertFalse(userRepository.existsByEmail(testEmail));
    }

    @Test
    void registrationWithInvalidPhoneFormatShouldFailWith400BadRequest() throws Exception {
        String testEmail = "invalid.phone." + System.currentTimeMillis() + "@talentus.app";
        RegisterTutorRequest request = new RegisterTutorRequest(
                "Luisa Marín",
                testEmail,
                "PasswordSeguro2026*",
                "04121234567", // Falta formato internacional +
                true
        );

        mockMvc.perform(post("/api/v1/auth/register-tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumber").exists());

        assertFalse(userRepository.existsByEmail(testEmail));
    }

    @Test
    void registrationWithExistingEmailShouldFailWith409Conflict() throws Exception {
        RegisterTutorRequest request = new RegisterTutorRequest(
                "Admin Duplicado",
                "admin@talentus.app", // Email ya registrado
                "PasswordSeguro2026*",
                "+584120000000",
                true
        );

        mockMvc.perform(post("/api/v1/auth/register-tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }
}
