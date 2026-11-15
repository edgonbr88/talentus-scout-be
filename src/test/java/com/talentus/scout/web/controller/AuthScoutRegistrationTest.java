package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthScoutRegistrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private MockMultipartFile createIdDocumentFile() {
        return new MockMultipartFile(
                "idDocumentFile",
                "cedula.pdf",
                "application/pdf",
                "Fake PDF Cedula Content".getBytes()
        );
    }

    private MockMultipartFile createSelfieFile() {
        return new MockMultipartFile(
                "selfieFile",
                "selfie.jpg",
                "image/jpeg",
                "Fake JPG Selfie Content".getBytes()
        );
    }

    @Test
    void registerScout_WithValidData_Returns201AndPendingVerificationStatus() throws Exception {
        String email = "scout." + System.currentTimeMillis() + "@rayovallecano.es";

        MockMultipartFile credentialsFile = new MockMultipartFile(
                "credentialsFile",
                "credencial_oficial.pdf",
                "application/pdf",
                "Fake PDF Carnet Profesional Content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(credentialsFile)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .header("CF-Connecting-IP", "190.200.10.5")
                        .header("User-Agent", "TalentusScout-TestAgent/1.0")
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Mateo Falcon")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Academia Rayo Vallecano")
                        .param("scoutRole", "Reclutador Internacional")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("linkedinUrl", "https://linkedin.com/in/mateo-falcon")
                        .param("acceptTerms", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Mateo Falcon"))
                .andExpect(jsonPath("$.role").value("SCOUT"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.organizationName").value("Academia Rayo Vallecano"))
                .andExpect(jsonPath("$.credentialsDocumentUrl").isNotEmpty());

        // Verificar en Base de Datos
        Optional<User> userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertEquals(UserRole.SCOUT, user.getRole());
        assertEquals(AccountStatus.PENDING_VERIFICATION, user.getStatus());

        Optional<ScoutProfile> profileOpt = scoutProfileRepository.findByUserId(user.getId());
        assertTrue(profileOpt.isPresent());
        ScoutProfile profile = profileOpt.get();
        assertEquals(ScoutVerificationStatus.PENDING, profile.getVerificationStatus());
        assertEquals("Academia Rayo Vallecano", profile.getOrganizationName());
        assertEquals("Reclutador Internacional", profile.getScoutRole());
        assertEquals("V", profile.getIdDocumentType());
        assertEquals("12345678", profile.getIdDocumentNumber());
        assertTrue(profile.getCredentialsDocumentUrl().contains("/uploads/credentials/"));
        assertTrue(profile.getIdDocumentUrl().contains("/uploads/identity-documents/scouts/"));
        assertTrue(profile.getSelfieUrl().contains("/uploads/selfies/scouts/"));
        assertTrue(profile.isAcceptedTerms());
        assertEquals("1.0", profile.getTermsVersion());
        assertNotNull(profile.getTermsAcceptedAt());
        assertEquals("190.200.10.5", profile.getTermsIpAddress());
        assertEquals("TalentusScout-TestAgent/1.0", profile.getTermsUserAgent());
    }

    @Test
    void registerScout_DuplicateEmail_Returns409Conflict() throws Exception {
        String email = "scout.dup." + System.currentTimeMillis() + "@rayovallecano.es";

        MockMultipartFile credentialsFile = new MockMultipartFile(
                "credentialsFile",
                "credencial.pdf",
                "application/pdf",
                "PDF".getBytes()
        );

        // Primer registro exitoso
        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(credentialsFile)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Mateo Falcon")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Academia Rayo Vallecano")
                        .param("scoutRole", "Reclutador Internacional")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("acceptTerms", "true"))
                .andExpect(status().isCreated());

        // Segundo registro con el mismo email -> Conflict
        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(credentialsFile)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Mateo Falcon")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Academia Rayo Vallecano")
                        .param("scoutRole", "Reclutador Internacional")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("acceptTerms", "true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registerScout_MissingCredentialsFile_Returns400BadRequest() throws Exception {
        String email = "scout.nofile." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Scout Sin Archivo")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Club Prueba")
                        .param("scoutRole", "Scout")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerScout_MissingSelfieOrIdDocument_Returns400BadRequest() throws Exception {
        String email = "scout.noselfie." + System.currentTimeMillis() + "@test.com";
        MockMultipartFile credentialsFile = new MockMultipartFile(
                "credentialsFile",
                "credencial.pdf",
                "application/pdf",
                "PDF".getBytes()
        );

        // Sin selfie
        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(credentialsFile)
                        .file(createIdDocumentFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Scout Sin Selfie")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Club Prueba")
                        .param("scoutRole", "Scout")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerScout_WithOrganizationClubId_AssignsOfficialClubName() throws Exception {
        ClubOrganization club = clubOrganizationRepository.save(ClubOrganization.builder()
                .name("Deportivo La Guaira " + System.currentTimeMillis())
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build());

        String email = "scout.club." + System.currentTimeMillis() + "@dlaguaira.com";
        MockMultipartFile file = new MockMultipartFile("credentialsFile", "cred.pdf", "application/pdf", "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(file)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Ojeador La Guaira")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationClubId", club.getId().toString())
                        .param("scoutRole", "Ojeador Juvenil")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("acceptTerms", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationName").value(club.getName()));

        Optional<User> userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent());
        Optional<ScoutProfile> profileOpt = scoutProfileRepository.findByUserId(userOpt.get().getId());
        assertTrue(profileOpt.isPresent());
        assertEquals(club.getName(), profileOpt.get().getOrganizationName());
    }

    @Test
    void registerScout_WithCustomClubOther_CreatesUnverifiedClubOrganization() throws Exception {
        String customName = "Academia Los Andes " + System.currentTimeMillis();
        String email = "scout.custom." + System.currentTimeMillis() + "@losandes.com";
        MockMultipartFile file = new MockMultipartFile("credentialsFile", "cred.pdf", "application/pdf", "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(file)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Director Los Andes")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", customName)
                        .param("customClubCity", "Mérida")
                        .param("customClubState", "Mérida")
                        .param("customClubCountry", "Venezuela")
                        .param("scoutRole", "Director Deportivo")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("acceptTerms", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationName").value(customName));

        Optional<ClubOrganization> savedClub = clubOrganizationRepository.findByNameIgnoreCase(customName);
        assertTrue(savedClub.isPresent());
        assertEquals("Mérida", savedClub.get().getCity());
        assertEquals("Mérida", savedClub.get().getState());
        assertEquals("Venezuela", savedClub.get().getCountry());
        assertFalse(savedClub.get().isVerifiedOfficial());
    }

    @Test
    void registerScout_AsIndependentAgent_AssignsIndependent() throws Exception {
        String email = "scout.indep." + System.currentTimeMillis() + "@agente.com";
        MockMultipartFile file = new MockMultipartFile("credentialsFile", "cred.pdf", "application/pdf", "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(file)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Agente FIFA Libre")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Agente Independiente")
                        .param("scoutRole", "Agente FIFA")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("acceptTerms", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationName").value("Agente Independiente"));

        Optional<User> userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent());
        Optional<ScoutProfile> profileOpt = scoutProfileRepository.findByUserId(userOpt.get().getId());
        assertTrue(profileOpt.isPresent());
        assertEquals("Agente Independiente", profileOpt.get().getOrganizationName());
    }

    @Test
    void registerScout_WithoutAcceptedTerms_Returns400BadRequest() throws Exception {
        String email = "scout.noterms." + System.currentTimeMillis() + "@test.com";
        MockMultipartFile credentialsFile = new MockMultipartFile(
                "credentialsFile",
                "credencial.pdf",
                "application/pdf",
                "PDF".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/auth/register-scout")
                        .file(credentialsFile)
                        .file(createIdDocumentFile())
                        .file(createSelfieFile())
                        .param("email", email)
                        .param("password", "Password123*")
                        .param("fullName", "Scout Sin Terminos")
                        .param("phoneNumber", "+584121234567")
                        .param("organizationName", "Club Prueba")
                        .param("scoutRole", "Scout")
                        .param("idDocumentType", "V")
                        .param("idDocumentNumber", "12345678")
                        .param("acceptTerms", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
