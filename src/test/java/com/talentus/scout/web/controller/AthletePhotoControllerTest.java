package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
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
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AthletePhotoControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private String tutorToken;
    private String otherTutorToken;
    private UUID athleteId;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String email1 = "tutor.photo." + System.currentTimeMillis() + "@talentus.app";
        User tutorUser = new User(email1, passwordEncoder.encode("Password123*"), "Tutor Foto", "+584141113344", UserRole.TUTOR, AccountStatus.ACTIVE);
        tutorUser = userRepository.save(tutorUser);
        tutorToken = jwtService.generateToken(tutorUser);

        String email2 = "tutor.other." + System.currentTimeMillis() + "@talentus.app";
        User otherTutorUser = new User(email2, passwordEncoder.encode("Password123*"), "Otro Tutor", "+584149993344", UserRole.TUTOR, AccountStatus.ACTIVE);
        otherTutorUser = userRepository.save(otherTutorUser);
        otherTutorToken = jwtService.generateToken(otherTutorUser);

        // Crear un atleta para el tutor principal
        String payload = """
                {
                    "firstName": "Sebastian",
                    "lastName": "PhotoTest",
                    "birthDate": "2012-05-10",
                    "primaryPosition": "DELANTERO_CENTRO",
                    "preferredFoot": "DERECHO",
                    "nationality": "Venezuela"
                }
                """;

        String createResp = mockMvc.perform(post("/api/v1/athletes")
                        .header("Authorization", "Bearer " + tutorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String athleteIdStr = com.jayway.jsonpath.JsonPath.read(createResp, "$.id");
        this.athleteId = UUID.fromString(athleteIdStr);
        assertNotNull(this.athleteId);
    }

    @Test
    void uploadProfilePhoto_AsOwner_Success() throws Exception {
        MockMultipartFile validPhoto = new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                "fake image content binary".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + athleteId + "/photo")
                        .file(validPhoto)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(athleteId.toString()))
                .andExpect(jsonPath("$.profilePhotoUrl").value(containsString("/uploads/photos/")));
    }

    @Test
    void uploadProfilePhoto_AsOtherTutor_Forbidden() throws Exception {
        MockMultipartFile validPhoto = new MockMultipartFile(
                "file",
                "profile.png",
                "image/png",
                "fake image bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + athleteId + "/photo")
                        .file(validPhoto)
                        .header("Authorization", "Bearer " + otherTutorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadProfilePhoto_InvalidFormat_BadRequest() throws Exception {
        MockMultipartFile invalidPdf = new MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                "%PDF-1.4 fake content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + athleteId + "/photo")
                        .file(invalidPdf)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Formato no permitido")));
    }

    @Test
    void uploadProfilePhoto_Exceeds5MB_BadRequest() throws Exception {
        byte[] oversizedBytes = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile largePhoto = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                oversizedBytes
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + athleteId + "/photo")
                        .file(largePhoto)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("El archivo excede el tamaño máximo permitido")));
    }

    @Test
    void deleteProfilePhoto_AsOwner_Success() throws Exception {
        // Primero subir una foto
        MockMultipartFile photo = new MockMultipartFile(
                "file",
                "temp.webp",
                "image/webp",
                "binary webp data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/athletes/" + athleteId + "/photo")
                        .file(photo)
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhotoUrl").isNotEmpty());

        // Luego eliminarla
        mockMvc.perform(delete("/api/v1/athletes/" + athleteId + "/photo")
                        .header("Authorization", "Bearer " + tutorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhotoUrl").isEmpty());
    }

    @Test
    void deleteProfilePhoto_AsOtherTutor_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/athletes/" + athleteId + "/photo")
                        .header("Authorization", "Bearer " + otherTutorToken))
                .andExpect(status().isForbidden());
    }
}
