package com.talentus.scout.service;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AdminNotificationService adminNotificationService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminNotificationService, "adminFallbackEmail", "soporte@talentusscout.com");
        ReflectionTestUtils.setField(adminNotificationService, "frontendBaseUrl", "https://talentusscout.com");

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Admin Principal")
                .email("admin.audit@talentusscout.com")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Obtiene correos de administradores activos registrados en la base de datos")
    void getActiveAdminEmails_returnsDatabaseAdmins() {
        when(userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(adminUser));

        List<String> emails = adminNotificationService.getActiveAdminEmails();

        assertThat(emails).containsExactly("admin.audit@talentusscout.com");
    }

    @Test
    @DisplayName("Si no existen administradores activos, devuelve el correo fallback institucional")
    void getActiveAdminEmails_whenNoneInDb_returnsFallback() {
        when(userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<String> emails = adminNotificationService.getActiveAdminEmails();

        assertThat(emails).containsExactly("soporte@talentusscout.com");
    }

    @Test
    @DisplayName("notifyNewTutorRegistered envía alerta por correo con enlace a verificación legal")
    void notifyNewTutorRegistered_success() {
        when(userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(adminUser));
        when(emailService.getBrandingLogoUrl()).thenReturn("https://talentus.app/logo.png");

        User tutor = User.builder()
                .fullName("Carlos Mendoza")
                .email("carlos.mendoza@gmail.com")
                .phoneNumber("+584141234567")
                .state("Carabobo")
                .country("Venezuela")
                .build();

        TutorLegalConsent consent = TutorLegalConsent.builder()
                .idDocumentType("V")
                .idDocumentNumber("18765432")
                .idDocumentFileUrl("/uploads/docs/cedula.pdf")
                .build();

        adminNotificationService.notifyNewTutorRegistered(tutor, consent);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService, times(1)).sendAdminAlert(
                eq("admin.audit@talentusscout.com"),
                subjectCaptor.capture(),
                htmlCaptor.capture(),
                anyString()
        );

        assertThat(subjectCaptor.getValue()).contains("Nuevo Representante Registrado");
        assertThat(htmlCaptor.getValue()).contains("Carlos Mendoza");
        assertThat(htmlCaptor.getValue()).contains("V 18765432");
        assertThat(htmlCaptor.getValue()).contains("https://talentusscout.com/admin/verificacion-legal");
    }

    @Test
    @DisplayName("notifyNewAthleteRegistered envía alerta por correo con datos del atleta y tutor")
    void notifyNewAthleteRegistered_success() {
        when(userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(adminUser));
        when(emailService.getBrandingLogoUrl()).thenReturn("https://talentus.app/logo.png");

        User tutor = User.builder()
                .fullName("María Pérez")
                .email("maria.perez@gmail.com")
                .build();

        ClubOrganization club = ClubOrganization.builder()
                .name("Academia Secasports")
                .build();

        Athlete athlete = Athlete.builder()
                .firstName("Alejandro")
                .lastName("Pérez")
                .birthDate(LocalDate.of(2011, 4, 15))
                .primaryPosition("Medio Centro")
                .currentClub(club)
                .tutor(tutor)
                .build();

        adminNotificationService.notifyNewAthleteRegistered(athlete);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendAdminAlert(
                eq("admin.audit@talentusscout.com"),
                contains("Nuevo Atleta Inscrito"),
                htmlCaptor.capture(),
                anyString()
        );

        assertThat(htmlCaptor.getValue()).contains("Alejandro Pérez");
        assertThat(htmlCaptor.getValue()).contains("Academia Secasports");
        assertThat(htmlCaptor.getValue()).contains("María Pérez");
        assertThat(htmlCaptor.getValue()).contains("https://talentusscout.com/admin/verificacion-legal");
    }

    @Test
    @DisplayName("notifyNewCustomClubCreated envía alerta de homologación de club con enlace a /admin/clubes")
    void notifyNewCustomClubCreated_success() {
        when(userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(adminUser));
        when(emailService.getBrandingLogoUrl()).thenReturn("https://talentus.app/logo.png");

        ClubOrganization club = ClubOrganization.builder()
                .name("Deportivo Petare Juvenil")
                .city("Caracas")
                .state("Miranda")
                .country("Venezuela")
                .verifiedOfficial(false)
                .build();

        adminNotificationService.notifyNewCustomClubCreated(club, "scout.baruta@gmail.com");

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendAdminAlert(
                eq("admin.audit@talentusscout.com"),
                contains("Nueva Organización Registrada por Comunidad"),
                htmlCaptor.capture(),
                anyString()
        );

        assertThat(htmlCaptor.getValue()).contains("Deportivo Petare Juvenil");
        assertThat(htmlCaptor.getValue()).contains("scout.baruta@gmail.com");
        assertThat(htmlCaptor.getValue()).contains("https://talentusscout.com/admin/clubes");
    }

    @Test
    @DisplayName("notifyNewScoutRegistered envía alerta de acreditación con enlace a /admin/scouts")
    void notifyNewScoutRegistered_success() {
        when(userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(adminUser));
        when(emailService.getBrandingLogoUrl()).thenReturn("https://talentus.app/logo.png");

        User scoutUser = User.builder()
                .fullName("Roberto Veedor")
                .email("roberto.scout@club.com")
                .phoneNumber("+584120000000")
                .build();

        ScoutProfile scout = ScoutProfile.builder()
                .user(scoutUser)
                .organizationName("Caracas FC")
                .scoutRole("Director de Captación")
                .credentialsDocumentUrl("/uploads/scout/cred.pdf")
                .linkedinUrl("https://linkedin.com/in/roberto-scout")
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .build();

        adminNotificationService.notifyNewScoutRegistered(scout);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendAdminAlert(
                eq("admin.audit@talentusscout.com"),
                contains("Nueva Solicitud de Acreditación de Scout"),
                htmlCaptor.capture(),
                anyString()
        );

        assertThat(htmlCaptor.getValue()).contains("Roberto Veedor");
        assertThat(htmlCaptor.getValue()).contains("Caracas FC");
        assertThat(htmlCaptor.getValue()).contains("Director de Captación");
        assertThat(htmlCaptor.getValue()).contains("https://talentusscout.com/admin/scouts");
    }
}
