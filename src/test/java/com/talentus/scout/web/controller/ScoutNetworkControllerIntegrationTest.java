package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutNetworkContactLog;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutContactChannel;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutNetworkContactLogRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ScoutNetworkControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private ScoutNetworkContactLogRepository contactLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private ClubOrganization clubCaracas;
    private ClubOrganization clubBogota;

    private User viewerScoutUser;
    private ScoutProfile viewerScoutProfile;
    private String viewerScoutToken;

    private ScoutProfile sameClubColleagueProfile;
    private ScoutProfile otherClubColleagueProfile;
    private ScoutProfile hiddenColleagueProfile;
    private ScoutProfile pendingColleagueProfile;
    private String pendingScoutToken;
    private String tutorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        long ts = System.currentTimeMillis();

        clubCaracas = clubOrganizationRepository.save(ClubOrganization.builder()
                .name("Caracas Network FC " + ts)
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .verifiedOfficial(true)
                .logoUrl("https://cdn.talentus.app/logos/ccs-" + ts + ".png")
                .build());

        clubBogota = clubOrganizationRepository.save(ClubOrganization.builder()
                .name("Bogota Scouting Club " + ts)
                .city("Bogotá")
                .state("Cundinamarca")
                .country("Colombia")
                .verifiedOfficial(false)
                .build());

        viewerScoutUser = userRepository.save(User.builder()
                .email("viewer.scout." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Roberto Viewer")
                .phoneNumber("+584121110001")
                .country("Venezuela")
                .state("Distrito Capital")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());
        viewerScoutToken = jwtService.generateToken(viewerScoutUser);

        viewerScoutProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(viewerScoutUser)
                .organizationName(clubCaracas.getName())
                .scoutRole("Director Deportivo")
                .credentialsDocumentUrl("/uploads/credentials/viewer-" + ts + ".pdf")
                .selfieUrl("/uploads/selfies/viewer-secret-" + ts + ".jpg")
                .idDocumentUrl("/uploads/identity-documents/viewer-id-" + ts + ".pdf")
                .idDocumentNumber("V-10000001")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .showInNetwork(true)
                .showWhatsappInNetwork(true)
                .showEmailInNetwork(true)
                .build());

        User sameClubUser = userRepository.save(User.builder()
                .email("colleague.same." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Andrea Colega Mismo Club")
                .phoneNumber("+584142220002")
                .country("Venezuela")
                .state("Miranda")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());

        sameClubColleagueProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(sameClubUser)
                .organizationName(clubCaracas.getName())
                .scoutRole("Ojeadora Sub-17")
                .credentialsDocumentUrl("/uploads/credentials/same-" + ts + ".pdf")
                .selfieUrl("/uploads/selfies/same-kyc-secret.jpg")
                .idDocumentNumber("V-18888888")
                .profilePhotoUrl("https://cdn.talentus.app/photos/scouts/andrea.jpg")
                .linkedinUrl("https://www.linkedin.com/in/andrea-scout")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .showInNetwork(true)
                .showWhatsappInNetwork(true)
                .showEmailInNetwork(false)
                .build());

        User otherClubUser = userRepository.save(User.builder()
                .email("colleague.other." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Diego Internacional")
                .phoneNumber("+573001112233")
                .country("Colombia")
                .state("Cundinamarca")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());

        otherClubColleagueProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(otherClubUser)
                .organizationName(clubBogota.getName())
                .scoutRole("Scout Internacional")
                .credentialsDocumentUrl("/uploads/credentials/other-" + ts + ".pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .showInNetwork(true)
                .showWhatsappInNetwork(false)
                .showEmailInNetwork(true)
                .build());

        User hiddenUser = userRepository.save(User.builder()
                .email("colleague.hidden." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Oculto Privacidad")
                .phoneNumber("+584129990003")
                .country("Venezuela")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());

        hiddenColleagueProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(hiddenUser)
                .organizationName(clubCaracas.getName())
                .scoutRole("Analista")
                .credentialsDocumentUrl("/uploads/credentials/hidden-" + ts + ".pdf")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .showInNetwork(false)
                .build());

        User pendingUser = userRepository.save(User.builder()
                .email("scout.pending." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Scout Pendiente")
                .phoneNumber("+584129990004")
                .role(UserRole.SCOUT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build());
        pendingScoutToken = jwtService.generateToken(pendingUser);

        pendingColleagueProfile = scoutProfileRepository.save(ScoutProfile.builder()
                .user(pendingUser)
                .organizationName(clubCaracas.getName())
                .scoutRole("Scout")
                .credentialsDocumentUrl("/uploads/credentials/pending-" + ts + ".pdf")
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .showInNetwork(true)
                .build());

        User tutorUser = userRepository.save(User.builder()
                .email("tutor.net." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Tutor Bloqueado")
                .phoneNumber("+584129990005")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());
        tutorToken = jwtService.generateToken(tutorUser);

        User adminUser = userRepository.save(User.builder()
                .email("admin.net." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("Admin Network")
                .phoneNumber("+584129990006")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
        adminToken = jwtService.generateToken(adminUser);
    }

    @Test
    void shouldListVisibleApprovedScoutsWithoutExposingKycOrRawContactData() throws Exception {
        String responseJson = mockMvc.perform(get("/api/v1/scouts/network")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].scoutProfileId", hasItem(sameClubColleagueProfile.getId().toString())))
                .andExpect(jsonPath("$.content[*].scoutProfileId", hasItem(otherClubColleagueProfile.getId().toString())))
                .andExpect(jsonPath("$.content[*].scoutProfileId", not(hasItem(viewerScoutProfile.getId().toString()))))
                .andExpect(jsonPath("$.content[*].scoutProfileId", not(hasItem(hiddenColleagueProfile.getId().toString()))))
                .andExpect(jsonPath("$.content[*].scoutProfileId", not(hasItem(pendingColleagueProfile.getId().toString()))))
                .andExpect(jsonPath("$.content[?(@.scoutProfileId=='" + sameClubColleagueProfile.getId() + "')].sameClubAsViewer", hasItem(true)))
                .andExpect(jsonPath("$.content[?(@.scoutProfileId=='" + sameClubColleagueProfile.getId() + "')].whatsappAvailable", hasItem(true)))
                .andExpect(jsonPath("$.content[?(@.scoutProfileId=='" + sameClubColleagueProfile.getId() + "')].emailAvailable", hasItem(false)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Aislamiento estricto KYC y anti-scraping
        assertFalse(responseJson.contains("same-kyc-secret.jpg"), "No debe exponer selfieUrl");
        assertFalse(responseJson.contains("V-18888888"), "No debe exponer idDocumentNumber");
        assertFalse(responseJson.contains("+584142220002"), "No debe exponer teléfono crudo");
        assertFalse(responseJson.contains("colleague.same."), "No debe exponer email crudo");
    }

    @Test
    void shouldFilterBySameClubQueryCountryAndClubIdAndReturnFacets() throws Exception {
        mockMvc.perform(get("/api/v1/scouts/network")
                        .param("sameClubOnly", "true")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].scoutProfileId", hasItem(sameClubColleagueProfile.getId().toString())))
                .andExpect(jsonPath("$.content[*].scoutProfileId", not(hasItem(otherClubColleagueProfile.getId().toString()))));

        mockMvc.perform(get("/api/v1/scouts/network")
                        .param("query", "Internacional")
                        .param("country", "Colombia")
                        .param("clubId", clubBogota.getId().toString())
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].scoutProfileId", hasItem(otherClubColleagueProfile.getId().toString())))
                .andExpect(jsonPath("$.content[*].scoutProfileId", not(hasItem(sameClubColleagueProfile.getId().toString()))));

        mockMvc.perform(get("/api/v1/scouts/network/filters")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerOrganizationName").value(clubCaracas.getName()))
                .andExpect(jsonPath("$.canFilterSameClub").value(true))
                .andExpect(jsonPath("$.countries", hasItems("Venezuela", "Colombia")));
    }

    @Test
    void shouldGenerateAuditedContactUrlsAndEnforcePrivacySettings() throws Exception {
        // WhatsApp permitido para sameClubColleagueProfile
        mockMvc.perform(post("/api/v1/scouts/network/" + sameClubColleagueProfile.getId() + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\"}")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.contactUrl", startsWith("https://wa.me/584142220002?text=")));

        List<ScoutNetworkContactLog> logs = contactLogRepository.findByRequesterIdOrderByCreatedAtDesc(viewerScoutUser.getId());
        assertEquals(1, logs.size());
        assertEquals(ScoutContactChannel.WHATSAPP, logs.get(0).getChannel());

        // Email deshabilitado por privacidad para sameClubColleagueProfile -> 403
        mockMvc.perform(post("/api/v1/scouts/network/" + sameClubColleagueProfile.getId() + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isForbidden());

        // Email permitido para otherClubColleagueProfile -> mailto:
        mockMvc.perform(post("/api/v1/scouts/network/" + otherClubColleagueProfile.getId() + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.contactUrl", startsWith("mailto:")));

        // Auto-contacto rechazado -> 400
        mockMvc.perform(post("/api/v1/scouts/network/" + viewerScoutProfile.getId() + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\"}")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldManageScoutNetworkPrivacySettingsAndPublicPhoto() throws Exception {
        mockMvc.perform(put("/api/v1/scout/profile/network-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showInNetwork\":false,\"showWhatsappInNetwork\":false,\"showEmailInNetwork\":true,\"linkedinUrl\":\"https://linkedin.com/in/roberto-viewer\",\"country\":\"España\",\"state\":\"Madrid\"}")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showInNetwork").value(false))
                .andExpect(jsonPath("$.showWhatsappInNetwork").value(false))
                .andExpect(jsonPath("$.showEmailInNetwork").value(true))
                .andExpect(jsonPath("$.country").value("España"))
                .andExpect(jsonPath("$.state").value("Madrid"));

        // Subir foto pública válida
        MockMultipartFile validPhoto = new MockMultipartFile("file", "avatar.png", "image/png", "png-data".getBytes());
        mockMvc.perform(multipart("/api/v1/scout/profile/photo")
                        .file(validPhoto)
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhotoUrl", containsString("photos/scouts")));

        // Rechazar archivo no imagen
        MockMultipartFile invalidFile = new MockMultipartFile("file", "doc.pdf", "application/pdf", "pdf-data".getBytes());
        mockMvc.perform(multipart("/api/v1/scout/profile/photo")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isBadRequest());

        // Eliminar foto pública
        mockMvc.perform(delete("/api/v1/scout/profile/photo")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/scout/profile/network-settings")
                        .header("Authorization", "Bearer " + viewerScoutToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhotoUrl").value(nullValue()));
    }

    @Test
    void shouldEnforceRoleBasedAccessControl() throws Exception {
        // Tutor bloqueado -> 403
        mockMvc.perform(get("/api/v1/scouts/network")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isForbidden());

        // Scout pendiente bloqueado -> 403
        mockMvc.perform(get("/api/v1/scouts/network")
                        .header("Authorization", "Bearer " + pendingScoutToken))
                .andExpect(status().isForbidden());

        // Admin puede ver el directorio de red -> 200
        mockMvc.perform(get("/api/v1/scouts/network")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Admin no puede editar perfil de scout -> 403
        mockMvc.perform(get("/api/v1/scout/profile/network-settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
