package com.talentus.scout.service.mail;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
