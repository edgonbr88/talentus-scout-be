package com.talentus.scout.service.mail;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @InjectMocks
    private EmailService emailService;

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);
        ReflectionTestUtils.setField(emailService, "mailFrom", "contacto@talentusscout.com");
        ReflectionTestUtils.setField(emailService, "apiKey", "");
        ReflectionTestUtils.setField(emailService, "brandingLogoUrl", "https://i.imgur.com/9j8oQ2B.png");
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://talentusscout.com");
        emailService.init();
    }

    @Test
    @DisplayName("Debe cargar plantillas de correo y contener la URL oficial del logo")
    void testTemplatesContainOfficialLogo() {
        String verificationTemplate = (String) ReflectionTestUtils.getField(emailService, "emailVerificationTemplate");
        String inviteTemplate = (String) ReflectionTestUtils.getField(emailService, "certificationInviteTemplate");

        assertThat(verificationTemplate).isNotNull();
        assertThat(verificationTemplate).contains("https://i.imgur.com/9j8oQ2B.png");

        assertThat(inviteTemplate).isNotNull();
        assertThat(inviteTemplate).contains("https://i.imgur.com/9j8oQ2B.png");
    }

    @Test
    @DisplayName("sendEmailVerification debe retornar true en modo mock cuando mailEnabled=false")
    void testSendEmailVerificationMock() {
        boolean sent = emailService.sendEmailVerification("tutor@example.com", "Carlos Perez", "https://talentusscout.com/verify?token=123");
        assertThat(sent).isTrue();
        verifyNoInteractions(resend);
    }

    @Test
    @DisplayName("sendCertificationInvite debe retornar true en modo mock")
    void testSendCertificationInviteMock() {
        boolean sent = emailService.sendCertificationInvite(
                "coach@club.com", "Entrenador Luis", "Juan Perez",
                "Carlos Perez", "Acreditación Física", "Ficha Médica y Antropométrica",
                "https://talentusscout.com/certify/token-abc", 7
        );
        assertThat(sent).isTrue();
        verifyNoInteractions(resend);
    }

    @Test
    @DisplayName("sendPaymentStatusNotification debe procesar notificación aprobada y rechazada en modo mock")
    void testSendPaymentStatusNotificationMock() {
        boolean sentApproved = emailService.sendPaymentStatusNotification(
                "tutor@example.com", "Carlos Perez", "Juan Perez", "Plan Pro Trimestral", true, null
        );
        assertThat(sentApproved).isTrue();

        boolean sentRejected = emailService.sendPaymentStatusNotification(
                "tutor@example.com", "Carlos Perez", "Juan Perez", "Plan Pro Trimestral", false, "Referencia inválida"
        );
        assertThat(sentRejected).isTrue();
        verifyNoInteractions(resend);
    }

    @Test
    @DisplayName("sendLegalVerificationResolutionNotification debe procesar notificación en modo mock")
    void testSendLegalVerificationResolutionNotificationMock() {
        boolean sent = emailService.sendLegalVerificationResolutionNotification(
                "tutor@example.com", "Carlos Perez", "Juan Perez", true, null
        );
        assertThat(sent).isTrue();
        verifyNoInteractions(resend);
    }

    @Test
    @DisplayName("sendScoutVerificationResolutionNotification debe procesar notificación de acreditación en modo mock")
    void testSendScoutVerificationResolutionNotificationMock() {
        boolean sent = emailService.sendScoutVerificationResolutionNotification(
                "scout@club.com", "Mauricio Ojeador", true, null
        );
        assertThat(sent).isTrue();
        verifyNoInteractions(resend);
    }

    @Test
    @DisplayName("sendBadgeCertificationResolutionNotification debe procesar resolución de insignia en modo mock")
    void testSendBadgeCertificationResolutionNotificationMock() {
        boolean sent = emailService.sendBadgeCertificationResolutionNotification(
                "tutor@example.com", "Carlos Perez", "Juan Perez", "Goleador Torneo", "Mérito Deportivo", true, null
        );
        assertThat(sent).isTrue();
        verifyNoInteractions(resend);
    }

    @Test
    @DisplayName("Debe despachar correo real exitosamente vía Resend cuando mailEnabled=true y apiKey presente")
    void testDispatchEmailRealSuccess() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "apiKey", "re_test_key_12345");

        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("resend_msg_test_abc123");
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        boolean sent = emailService.sendEmailVerification("destinatario@real.com", "Tutor Real", "https://talentusscout.com/verify?token=xyz");

        assertThat(sent).isTrue();
        verify(resend, times(1)).emails();
        verify(emails, times(1)).send(any(CreateEmailOptions.class));
    }

    @Test
    @DisplayName("Debe manejar excepciones de Resend retornando false sin propagar error")
    void testDispatchEmailHandlesResendException() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "apiKey", "re_test_key_12345");

        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new RuntimeException("Simulated Resend API 401 Unauthorized"));

        boolean sent = emailService.sendEmailVerification("destinatario@real.com", "Tutor Real", "https://talentusscout.com/verify?token=xyz");

        assertThat(sent).isFalse();
        verify(resend, times(1)).emails();
        verify(emails, times(1)).send(any(CreateEmailOptions.class));
    }

    @Test
    @DisplayName("sendScoutVerificationResolutionNotification debe incluir frontendBaseUrl dinámica y ningún dominio obsoleto")
    void testSendScoutVerificationResolutionNotificationDynamicLinks() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "apiKey", "re_test_key_12345");
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://talentusscout.com");

        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("resend_scout_123");
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);

        // Caso 1: Aprobado -> /scout/directory
        emailService.sendScoutVerificationResolutionNotification("scout@club.com", "Mauricio Ojeador", true, null);
        verify(emails, times(1)).send(captor.capture());
        String htmlApproved = captor.getValue().getHtml();

        assertThat(htmlApproved).contains("href='https://talentusscout.com/scout/directory'");
        assertThat(htmlApproved).doesNotContain("talentus.app");

        // Caso 2: Rechazado / Subsanación -> /login
        emailService.sendScoutVerificationResolutionNotification("scout@club.com", "Mauricio Ojeador", false, "Credencial ilegible");
        verify(emails, times(2)).send(captor.capture());
        String htmlRejected = captor.getValue().getHtml();

        assertThat(htmlRejected).contains("href='https://talentusscout.com/login'");
        assertThat(htmlRejected).doesNotContain("talentus.app");
    }

    @Test
    @DisplayName("sendLegalVerificationResolutionNotification debe incluir frontendBaseUrl dinámica hacia /dashboard")
    void testSendLegalVerificationResolutionNotificationDynamicLinks() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "apiKey", "re_test_key_12345");
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://talentusscout.com");

        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("resend_legal_123");
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);

        // Aprobado
        emailService.sendLegalVerificationResolutionNotification("tutor@example.com", "Carlos", "Juan", true, null);
        verify(emails, times(1)).send(captor.capture());
        String htmlApproved = captor.getValue().getHtml();
        assertThat(htmlApproved).contains("href='https://talentusscout.com/dashboard'");
        assertThat(htmlApproved).doesNotContain("talentus.app");

        // Observado
        emailService.sendLegalVerificationResolutionNotification("tutor@example.com", "Carlos", "Juan", false, "Partida borrosa");
        verify(emails, times(2)).send(captor.capture());
        String htmlRejected = captor.getValue().getHtml();
        assertThat(htmlRejected).contains("href='https://talentusscout.com/dashboard'");
        assertThat(htmlRejected).doesNotContain("talentus.app");
    }

    @Test
    @DisplayName("sendBadgeCertificationResolutionNotification debe incluir frontendBaseUrl dinámica hacia /dashboard")
    void testSendBadgeCertificationResolutionNotificationDynamicLinks() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "apiKey", "re_test_key_12345");
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://talentusscout.com");

        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("resend_badge_123");
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);

        // Aprobado
        emailService.sendBadgeCertificationResolutionNotification("tutor@example.com", "Carlos", "Juan", "Velocidad", "Física", true, null);
        verify(emails, times(1)).send(captor.capture());
        assertThat(captor.getValue().getHtml()).contains("href='https://talentusscout.com/dashboard'");
        assertThat(captor.getValue().getHtml()).doesNotContain("talentus.app");

        // Observado
        emailService.sendBadgeCertificationResolutionNotification("tutor@example.com", "Carlos", "Juan", "Velocidad", "Física", false, "Sin firma");
        verify(emails, times(2)).send(captor.capture());
        assertThat(captor.getValue().getHtml()).contains("href='https://talentusscout.com/dashboard'");
        assertThat(captor.getValue().getHtml()).doesNotContain("talentus.app");
    }

    @Test
    @DisplayName("sendScoutClubRevocationNotification debe incluir frontendBaseUrl dinámica hacia /login")
    void testSendScoutClubRevocationNotificationDynamicLinks() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "apiKey", "re_test_key_12345");
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://talentusscout.com");

        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("resend_revocation_123");
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);

        emailService.sendScoutClubRevocationNotification("scout@club.com", "Mauricio Ojeador", "Academia Futuro", "Club disuelto");
        verify(emails, times(1)).send(captor.capture());
        assertThat(captor.getValue().getHtml()).contains("href='https://talentusscout.com/login'");
        assertThat(captor.getValue().getHtml()).doesNotContain("talentus.app");
    }

    @Test
    @DisplayName("getBaseUrl debe normalizar trailing slash y usar fallback si está vacío")
    void testGetBaseUrlNormalization() {
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://talentusscout.com/");
        assertThat(emailService.getBaseUrl()).isEqualTo("https://talentusscout.com");

        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "http://localhost:4200");
        assertThat(emailService.getBaseUrl()).isEqualTo("http://localhost:4200");

        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "   ");
        assertThat(emailService.getBaseUrl()).isEqualTo("https://talentusscout.com");

        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", null);
        assertThat(emailService.getBaseUrl()).isEqualTo("https://talentusscout.com");
    }
}
