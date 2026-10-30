package com.talentus.scout.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AthleteIdentityDocumentTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void uploadMinorIdentityDocumentByOwnerTutorShouldSucceed() throws Exception {
        String tutorEmail = "tutor.doc." + System.currentTimeMillis() + "@talentus.app";
        User tutor = User.builder()
                .email(tutorEmail)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Roberto Díaz")
                .phoneNumber("+584141112233")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        User savedTutor = userRepository.save(tutor);

        String jwt = jwtService.generateToken(savedTutor);

        Athlete athlete = Athlete.builder()
                .tutor(savedTutor)
                .firstName("Samuel")
                .lastName("Díaz")
                .birthDate(LocalDate.of(2012, 5, 14))
                .gender("MALE")
                .primaryPosition("MEDIOCAMPISTA")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("samuel-diaz-" + System.currentTimeMillis())
                .isPublic(true)
                .build();
        Athlete savedAthlete = athleteRepository.save(athlete);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "partida_nacimiento.pdf",
                "application/pdf",
                "dummy pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + savedAthlete.getId() + "/identity-document")
                        .file(file)
                        .param("documentType", "PARTIDA_NACIMIENTO")
                        .param("documentNumber", "PN-2012-0045")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedAthlete.getId().toString()))
                .andExpect(jsonPath("$.identityDocumentType").value("PARTIDA_NACIMIENTO"))
                .andExpect(jsonPath("$.identityDocumentNumber").value("PN-2012-0045"))
                .andExpect(jsonPath("$.hasIdentityDocument").value(true))
                .andExpect(jsonPath("$.identityDocumentFileUrl").isString());

        Athlete updatedAthlete = athleteRepository.findById(savedAthlete.getId()).orElseThrow();
        assertEquals("PARTIDA_NACIMIENTO", updatedAthlete.getIdentityDocumentType());
        assertEquals("PN-2012-0045", updatedAthlete.getIdentityDocumentNumber());
        assertNotNull(updatedAthlete.getIdentityDocumentFileUrl());
    }

    @Test
    void uploadMinorIdentityDocumentByNonOwnerShouldFailWith403() throws Exception {
        User otherUser = User.builder()
                .email("other." + System.currentTimeMillis() + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Otro Usuario")
                .phoneNumber("+584149998877")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        User savedOther = userRepository.save(otherUser);
        String otherJwt = jwtService.generateToken(savedOther);

        // Atleta perteneciente al tutor original
        User originalTutor = User.builder()
                .email("original." + System.currentTimeMillis() + "@talentus.app")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Tutor Original")
                .phoneNumber("+584145556677")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        userRepository.save(originalTutor);

        Athlete athlete = Athlete.builder()
                .tutor(originalTutor)
                .firstName("Luis")
                .lastName("Original")
                .birthDate(LocalDate.of(2013, 1, 10))
                .gender("MALE")
                .primaryPosition("DELANTERO")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("luis-original-" + System.currentTimeMillis())
                .isPublic(true)
                .build();
        Athlete savedAthlete = athleteRepository.save(athlete);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cedula.png",
                "image/png",
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + savedAthlete.getId() + "/identity-document")
                        .file(file)
                        .param("documentType", "CEDULA")
                        .param("documentNumber", "V-30111222")
                        .header("Authorization", "Bearer " + otherJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadMinorIdentityDocumentExceeding5MBShouldFailWith400() throws Exception {
        String tutorEmail = "tutor.size." + System.currentTimeMillis() + "@talentus.app";
        User tutor = User.builder()
                .email(tutorEmail)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Tutor Tamaño")
                .phoneNumber("+584149991122")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        User savedTutor = userRepository.save(tutor);
        String jwt = jwtService.generateToken(savedTutor);

        Athlete athlete = Athlete.builder()
                .tutor(savedTutor)
                .firstName("Marcos")
                .lastName("Tamaño")
                .birthDate(LocalDate.of(2014, 2, 20))
                .gender("MALE")
                .primaryPosition("DEFENSA")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("marcos-tamano-" + System.currentTimeMillis())
                .isPublic(true)
                .build();
        Athlete savedAthlete = athleteRepository.save(athlete);

        // Archivo que excede 5MB (5MB + 1 byte)
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 10];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "partida_pesada.pdf",
                "application/pdf",
                largeBytes
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + savedAthlete.getId() + "/identity-document")
                        .file(largeFile)
                        .param("documentType", "PARTIDA_NACIMIENTO")
                        .param("documentNumber", "PN-2014-999")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El archivo excede el tamaño máximo permitido (5MB)"));
    }

    @Test
    void uploadMinorIdentityDocumentWithBlankDocumentNumberShouldFailWith400() throws Exception {
        String tutorEmail = "tutor.blank." + System.currentTimeMillis() + "@talentus.app";
        User tutor = User.builder()
                .email(tutorEmail)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Tutor Folio")
                .phoneNumber("+584149993344")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        User savedTutor = userRepository.save(tutor);
        String jwt = jwtService.generateToken(savedTutor);

        Athlete athlete = Athlete.builder()
                .tutor(savedTutor)
                .firstName("Gabriel")
                .lastName("Folio")
                .birthDate(LocalDate.of(2013, 8, 11))
                .gender("MALE")
                .primaryPosition("MEDIOCAMPISTA")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("gabriel-folio-" + System.currentTimeMillis())
                .isPublic(true)
                .build();
        Athlete savedAthlete = athleteRepository.save(athlete);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cedula.png",
                "image/png",
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + savedAthlete.getId() + "/identity-document")
                        .file(file)
                        .param("documentType", "CEDULA")
                        .param("documentNumber", "   ")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadMinorIdentityDocumentWithInvalidFormatShouldFailWith400() throws Exception {
        String tutorEmail = "tutor.format." + System.currentTimeMillis() + "@talentus.app";
        User tutor = User.builder()
                .email(tutorEmail)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Tutor Formato")
                .phoneNumber("+584149995566")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        User savedTutor = userRepository.save(tutor);
        String jwt = jwtService.generateToken(savedTutor);

        Athlete athlete = Athlete.builder()
                .tutor(savedTutor)
                .firstName("Diego")
                .lastName("Formato")
                .birthDate(LocalDate.of(2015, 3, 5))
                .gender("MALE")
                .primaryPosition("PORTERO")
                .preferredFoot("DIESTRO")
                .nationality("Venezolana")
                .slug("diego-formato-" + System.currentTimeMillis())
                .isPublic(true)
                .build();
        Athlete savedAthlete = athleteRepository.save(athlete);

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "malicioso.exe",
                "application/x-msdownload",
                "dummy exe content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + savedAthlete.getId() + "/identity-document")
                        .file(invalidFile)
                        .param("documentType", "CEDULA")
                        .param("documentNumber", "V-33111222")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato no permitido. Solo se aceptan archivos PDF, PNG o JPG."));
    }
}
