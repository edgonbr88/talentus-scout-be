package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteLegalConsentRepository;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthAthleteRegistrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private AthleteLegalConsentRepository athleteLegalConsentRepository;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void testRegisterAdultAthleteSuccess() throws Exception {
        String email = "adult.athlete." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Carlos Alejandro Silva")
                        .param("email", email)
                        .param("password", "AthletePassword123!")
                        .param("phoneNumber", "+584141234567")
                        .param("birthDate", "2005-06-15")
                        .param("primaryPosition", "DELANTERO")
                        .param("preferredFoot", "DERECHO")
                        .param("gender", "MALE")
                        .param("nationality", "Venezolana")
                        .param("acceptTerms", "true")
                        .param("acceptInstitutionalDiffusion", "true")
                        .header("X-Forwarded-For", "190.202.10.5")
                        .header("User-Agent", "Mozilla/5.0 TalentusApp/1.0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ATHLETE"))
                .andExpect(jsonPath("$.slug").isNotEmpty())
                .andExpect(jsonPath("$.athleteId").isNotEmpty());

        Optional<User> userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent(), "El usuario debió persistirse");
        User user = userOpt.get();
        assertEquals(UserRole.ATHLETE, user.getRole());

        List<Athlete> athletes = athleteRepository.findByTutorId(user.getId());
        assertEquals(1, athletes.size(), "Debe existir un único perfil de atleta vinculado");
        Athlete athlete = athletes.get(0);
        assertEquals(user.getId(), athlete.getTutor().getId(), "El tutor_id debe ser el propio id del usuario");
        assertEquals("Carlos Alejandro", athlete.getFirstName());
        assertEquals("Silva", athlete.getLastName());
        assertEquals(LocalDate.of(2005, 6, 15), athlete.getBirthDate());

        List<AthleteLegalConsent> consents = athleteLegalConsentRepository.findByAthleteId(athlete.getId());
        assertFalse(consents.isEmpty(), "Debe registrarse el consentimiento legal");
        AthleteLegalConsent consent = consents.get(0);
        assertTrue(consent.isAcceptedTerms());
        assertTrue(consent.isAcceptedInstitutionalDiffusion());
        assertEquals("190.202.10.5", consent.getIpAddress());
        assertEquals("Mozilla/5.0 TalentusApp/1.0", consent.getUserAgent());
    }

    @Test
    void testRegisterAthleteUnderageFails() throws Exception {
        String email = "underage." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Joven Menor")
                        .param("email", email)
                        .param("password", "Password123!")
                        .param("phoneNumber", "+584141234567")
                        .param("birthDate", "2012-05-10") // 14 años
                        .param("primaryPosition", "MEDIOCENTRO")
                        .param("preferredFoot", "DERECHO")
                        .param("acceptTerms", "true")
                        .param("acceptInstitutionalDiffusion", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("al menos 18 años")));

        assertFalse(userRepository.findByEmail(email).isPresent(), "No debió crearse usuario para menor de edad");
    }

    @Test
    void testRegisterAdultAthleteDuplicateEmailFails() throws Exception {
        String email = "duplicate.athlete." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Atleta Original")
                        .param("email", email)
                        .param("password", "Password123!")
                        .param("phoneNumber", "+584141234567")
                        .param("birthDate", "2004-01-01")
                        .param("primaryPosition", "DEFENSA")
                        .param("preferredFoot", "IZQUIERDO")
                        .param("acceptTerms", "true")
                        .param("acceptInstitutionalDiffusion", "true"))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Atleta Duplicado")
                        .param("email", email)
                        .param("password", "OtherPassword123!")
                        .param("phoneNumber", "+584149999999")
                        .param("birthDate", "2004-01-01")
                        .param("primaryPosition", "DELANTERO")
                        .param("preferredFoot", "DERECHO")
                        .param("acceptTerms", "true")
                        .param("acceptInstitutionalDiffusion", "true"))
                .andExpect(status().isConflict());
    }

    @Test
    void testRegisterAthleteWithoutAcceptingTermsFails() throws Exception {
        String email = "noterms." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Sin Terminos")
                        .param("email", email)
                        .param("password", "Password123!")
                        .param("phoneNumber", "+584141234567")
                        .param("birthDate", "2002-08-20")
                        .param("primaryPosition", "ARQUERO")
                        .param("preferredFoot", "DERECHO")
                        .param("acceptTerms", "false")
                        .param("acceptInstitutionalDiffusion", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAthleteCanAccessMyAthletes() throws Exception {
        String email = "athlete.access." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Atleta Acceso")
                        .param("email", email)
                        .param("password", "Password123!")
                        .param("phoneNumber", "+584141234567")
                        .param("birthDate", "2003-11-12")
                        .param("primaryPosition", "LATERAL")
                        .param("preferredFoot", "DERECHO")
                        .param("acceptTerms", "true")
                        .param("acceptInstitutionalDiffusion", "true"))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/v1/athletes/my-athletes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Atleta"))
                .andExpect(jsonPath("$[0].lastName").value("Acceso"));
    }

    @Test
    void testAthleteCannotCreateAnotherAthlete() throws Exception {
        String email = "athlete.single." + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(multipart("/api/v1/auth/register-athlete")
                        .param("fullName", "Atleta Unico")
                        .param("email", email)
                        .param("password", "Password123!")
                        .param("phoneNumber", "+584141234567")
                        .param("birthDate", "2001-04-10")
                        .param("primaryPosition", "DELANTERO")
                        .param("preferredFoot", "DERECHO")
                        .param("acceptTerms", "true")
                        .param("acceptInstitutionalDiffusion", "true"))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        String token = jwtService.generateToken(user);

        // POST /api/v1/athletes requires hasRole('TUTOR')
        mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Segundo\",\"lastName\":\"Atleta\",\"birthDate\":\"2010-01-01\",\"primaryPosition\":\"DEFENSA\",\"preferredFoot\":\"IZQUIERDO\"}"))
                .andExpect(status().isForbidden());
    }
}
