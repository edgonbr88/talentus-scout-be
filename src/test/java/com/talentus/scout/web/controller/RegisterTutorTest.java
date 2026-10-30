package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.EmailVerificationToken;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.EmailVerificationTokenRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void successfulTutorRegistrationShouldPersistUserInPendingEmailVerificationAndAllowActivationViaToken() throws Exception {
        String testEmail = "tutor.test." + System.currentTimeMillis() + "@talentus.app";

        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Mariana Gómez")
                        .param("email", testEmail)
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "+584149876543")
                        .param("acceptLopnnaTerms", "true")
                        .param("idDocumentType", "CEDULA")
                        .param("idDocumentNumber", "V-18456123")
                        .param("country", "Venezuela")
                        .param("state", "Miranda")
                        .param("address", "Calle 5 con Av. Principal, Los Teques")
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")
                        .header("X-Forwarded-For", "190.202.45.12"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.role").value("TUTOR"))
                .andExpect(jsonPath("$.fullName").value("Mariana Gómez"));

        // 1. Verificar persistencia en base de datos en estado PENDING_EMAIL_VERIFICATION
        Optional<User> savedUserOpt = userRepository.findByEmail(testEmail);
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals(UserRole.TUTOR, savedUser.getRole());
        assertEquals(AccountStatus.PENDING_EMAIL_VERIFICATION, savedUser.getStatus());
        assertEquals("+584149876543", savedUser.getPhoneNumber());
        assertEquals("Venezuela", savedUser.getCountry());
        assertEquals("Miranda", savedUser.getState());
        assertEquals("Calle 5 con Av. Principal, Los Teques", savedUser.getAddress());

        // 2. Verificar consentimiento legal LOPNNA con documento de identidad
        List<TutorLegalConsent> consents = tutorLegalConsentRepository.findByTutorId(savedUser.getId());
        assertEquals(1, consents.size());
        TutorLegalConsent consent = consents.get(0);
        assertTrue(consent.isAcceptedLopnnaTerms());
        assertEquals("1.0", consent.getTermsVersion());
        assertEquals("CEDULA", consent.getIdDocumentType());
        assertEquals("V-18456123", consent.getIdDocumentNumber());
        assertNotNull(consent.getIdDocumentFileUrl());
        assertEquals("190.202.45.12", consent.getIpAddress());
        assertTrue(consent.getUserAgent().contains("iPhone"));
        assertNotNull(consent.getAcceptedAt());

        // 3. Intento de login previo a verificación debe fallar con 403 Forbidden
        LoginRequest loginRequest = new LoginRequest(testEmail, "PasswordSeguro2026*");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_VERIFIED"));

        // 4. Obtener token generado y verificar email
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findTopByUserOrderByCreatedAtDesc(savedUser);
        assertTrue(tokenOpt.isPresent());
        String token = tokenOpt.get().getToken();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.role").value("TUTOR"));

        // 5. Usuario debe estar ACTIVE
        User verifiedUser = userRepository.findByEmail(testEmail).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, verifiedUser.getStatus());

        // 6. Login ahora debe ser exitoso
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void registrationWithLopnnaFalseShouldFailWith400BadRequest() throws Exception {
        String testEmail = "lopnna.false." + System.currentTimeMillis() + "@talentus.app";

        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Pedro Infante")
                        .param("email", testEmail)
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "+584121112233")
                        .param("acceptLopnnaTerms", "false") // Rechaza LOPNNA
                        .param("idDocumentType", "CEDULA")
                        .param("idDocumentNumber", "V-12345678")
                        .param("country", "Venezuela")
                        .param("state", "Miranda"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.acceptLopnnaTerms").value("Debe aceptar obligatoriamente los términos de protección integral del menor y consentimiento legal"));

        assertFalse(userRepository.existsByEmail(testEmail));
    }

    @Test
    void registrationWithInvalidPhoneFormatShouldFailWith400BadRequest() throws Exception {
        String testEmail = "invalid.phone." + System.currentTimeMillis() + "@talentus.app";

        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Luisa Marín")
                        .param("email", testEmail)
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "04121234567") // Falta formato internacional +
                        .param("acceptLopnnaTerms", "true")
                        .param("idDocumentType", "CEDULA")
                        .param("idDocumentNumber", "V-12345678")
                        .param("country", "Venezuela")
                        .param("state", "Miranda"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.phoneNumber").exists());

        assertFalse(userRepository.existsByEmail(testEmail));
    }

    @Test
    void registrationWithExistingEmailShouldFailWith409Conflict() throws Exception {
        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Admin Duplicado")
                        .param("email", "admin@talentus.app") // Email ya registrado
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "+584120000000")
                        .param("acceptLopnnaTerms", "true")
                        .param("idDocumentType", "CEDULA")
                        .param("idDocumentNumber", "V-12345678")
                        .param("country", "Venezuela")
                        .param("state", "Miranda"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void registrationWithUnauthorizedCountryShouldFailWith400BadRequest() throws Exception {
        String testEmail = "unauthorized.country." + System.currentTimeMillis() + "@talentus.app";

        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Carlos Gardel")
                        .param("email", testEmail)
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "+584121112233")
                        .param("acceptLopnnaTerms", "true")
                        .param("idDocumentType", "PASAPORTE")
                        .param("idDocumentNumber", "P-99887766")
                        .param("country", "Argentina") // País no autorizado
                        .param("state", "Buenos Aires"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.country").exists());

        assertFalse(userRepository.existsByEmail(testEmail));
    }

    @Test
    void registrationWithMissingStateShouldFailWith400BadRequest() throws Exception {
        String testEmail = "missing.state." + System.currentTimeMillis() + "@talentus.app";

        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Andrés Bello")
                        .param("email", testEmail)
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "+584121112233")
                        .param("acceptLopnnaTerms", "true")
                        .param("idDocumentType", "CEDULA")
                        .param("idDocumentNumber", "V-12345678")
                        .param("country", "Chile")) // Falta state
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.state").exists());

        assertFalse(userRepository.existsByEmail(testEmail));
    }

    @Test
    void registrationWithBoliviaShouldSucceedWith201Created() throws Exception {
        String testEmail = "tutor.bolivia." + System.currentTimeMillis() + "@talentus.app";

        MockMultipartFile idFile = new MockMultipartFile(
                "idDocumentFile",
                "cedula_bolivia.png",
                "image/png",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-tutor")
                        .file(idFile)
                        .param("fullName", "Marcelo Martins")
                        .param("email", testEmail)
                        .param("password", "PasswordSeguro2026*")
                        .param("phoneNumber", "+59171234567")
                        .param("acceptLopnnaTerms", "true")
                        .param("idDocumentType", "CI")
                        .param("idDocumentNumber", "LP-8877665")
                        .param("country", "Bolivia")
                        .param("state", "La Paz")
                        .param("address", "Av. Arce 2100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.email").value(testEmail));

        Optional<User> savedUserOpt = userRepository.findByEmail(testEmail);
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals("Bolivia", savedUser.getCountry());
        assertEquals("La Paz", savedUser.getState());
    }
}
