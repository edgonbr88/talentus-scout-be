package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.AthleteResponse;
import com.talentus.scout.web.dto.CreateAthleteRequest;
import com.talentus.scout.web.dto.RegisterAdultAthleteRequest;
import com.talentus.scout.web.dto.RegisterTutorRequest;
import com.talentus.scout.web.dto.UploadAthleteIdentityDocumentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class AdminNotificationFlowOptimizationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AthleteService athleteService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AdminNotificationService adminNotificationService;

    @MockitoBean
    private EmailService emailService;

    @Test
    @DisplayName("TS-84: registerTutor envía verificación al tutor y NO dispara alertas admin")
    void registerTutor_doesNotNotifyAdmin_butSendsVerificationEmail() {
        long ts = System.currentTimeMillis();
        String email = "tutor.ts84." + ts + "@talentus.test";
        MockMultipartFile docFile = new MockMultipartFile("idDocumentFile", "cedula.pdf", "application/pdf", "pdf-bytes".getBytes());
        MockMultipartFile selfieFile = new MockMultipartFile("selfieFile", "selfie.jpg", "image/jpeg", "img-bytes".getBytes());

        RegisterTutorRequest request = RegisterTutorRequest.builder()
                .fullName("Carlos Tutor TS84")
                .email(email)
                .password("Password123*")
                .phoneNumber("+584121112233")
                .country("Venezuela")
                .state("Distrito Capital")
                .address("Caracas")
                .acceptLopnnaTerms(true)
                .idDocumentType("V")
                .idDocumentNumber("18123456")
                .idDocumentFile(docFile)
                .selfieFile(selfieFile)
                .relationshipType("PARENT")
                .build();

        authService.registerTutor(request, "127.0.0.1", "JUnit");

        verify(emailService, times(1)).sendEmailVerification(eq(email), eq("Carlos Tutor TS84"), anyString());
        verify(adminNotificationService, never()).notifyNewAthleteRegistered(any());
        verify(adminNotificationService, never()).notifyNewScoutRegistered(any());
    }

    @Test
    @DisplayName("TS-84: createAthlete NO notifica al admin; uploadIdentityDocument SÍ notifica en carga inicial y re-carga tras rechazo")
    void athleteNotifications_triggeredOnlyOnIdentityDocumentUploadAndReupload() {
        long ts = System.currentTimeMillis();
        User tutor = userRepository.save(User.builder()
                .email("tutor.ath.ts84." + ts + "@talentus.test")
                .passwordHash(passwordEncoder.encode("Password123*"))
                .fullName("María Representante TS84")
                .phoneNumber("+584129998877")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .country("Venezuela")
                .state("Miranda")
                .build());

        CreateAthleteRequest createRequest = CreateAthleteRequest.builder()
                .firstName("Mateo")
                .lastName("Rivas")
                .birthDate(LocalDate.of(2011, 6, 15))
                .primaryPosition("DELANTERO_CENTRO")
                .preferredFoot("DERECHO")
                .nationality("Venezolana")
                .isPublic(true)
                .build();

        AthleteResponse created = athleteService.createAthlete(createRequest, tutor.getEmail());

        // 1. Al crear el atleta NO se notifica al administrador
        verify(adminNotificationService, never()).notifyNewAthleteRegistered(any());

        // 2. Al cargar el documento de identidad por primera vez SÍ se notifica al administrador
        UploadAthleteIdentityDocumentRequest uploadReq = UploadAthleteIdentityDocumentRequest.builder()
                .documentType("PARTIDA_NACIMIENTO")
                .documentNumber("PN-" + ts)
                .file(new MockMultipartFile("file", "partida.pdf", "application/pdf", "pdf-content".getBytes()))
                .build();

        athleteService.uploadIdentityDocument(created.getId(), uploadReq);
        verify(adminNotificationService, times(1)).notifyNewAthleteRegistered(any(Athlete.class));

        // 3. Simular rechazo administrativo y re-carga del documento -> SÍ vuelve a notificar
        Athlete persisted = athleteRepository.findById(created.getId()).orElseThrow();
        persisted.setLegalVerificationStatus(LegalVerificationStatus.REJECTED);
        persisted.setLegalVerificationNotes("Imagen borrosa");
        athleteRepository.save(persisted);

        UploadAthleteIdentityDocumentRequest reuploadReq = UploadAthleteIdentityDocumentRequest.builder()
                .documentType("PARTIDA_NACIMIENTO")
                .documentNumber("PN-" + ts + "-V2")
                .file(new MockMultipartFile("file", "partida_v2.pdf", "application/pdf", "pdf-content-v2".getBytes()))
                .build();

        athleteService.uploadIdentityDocument(created.getId(), reuploadReq);
        verify(adminNotificationService, times(2)).notifyNewAthleteRegistered(any(Athlete.class));
    }

    @Test
    @DisplayName("TS-84: registerAdultAthlete (+18) NO dispara notificación de patria potestad al admin")
    void registerAdultAthlete_doesNotNotifyAdmin() {
        long ts = System.currentTimeMillis();
        String email = "adult.ts84." + ts + "@talentus.test";
        MockMultipartFile docFile = new MockMultipartFile("idDocumentFile", "cedula.pdf", "application/pdf", "pdf-bytes".getBytes());
        MockMultipartFile selfieFile = new MockMultipartFile("selfieFile", "selfie.jpg", "image/jpeg", "img-bytes".getBytes());

        RegisterAdultAthleteRequest request = RegisterAdultAthleteRequest.builder()
                .fullName("Andrés Adulto TS84")
                .firstName("Andrés")
                .lastName("Adulto")
                .email(email)
                .password("Password123*")
                .phoneNumber("+584145556677")
                .country("Venezuela")
                .state("Carabobo")
                .address("Valencia")
                .birthDate(LocalDate.now().minusYears(19))
                .acceptTerms(true)
                .acceptInstitutionalDiffusion(true)
                .primaryPosition("MEDIOCAMPISTA_CENTRAL")
                .preferredFoot("DERECHO")
                .gender("MALE")
                .nationality("Venezolana")
                .idDocumentType("V")
                .idDocumentNumber("26123456")
                .idDocumentFile(docFile)
                .selfieFile(selfieFile)
                .build();

        authService.registerAdultAthlete(request, "127.0.0.1", "JUnit");

        verify(adminNotificationService, never()).notifyNewAthleteRegistered(any());
    }
}
