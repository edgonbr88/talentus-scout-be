package com.talentus.scout.service.mail;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private Resend resend;

    @Value("${talentus.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${talentus.mail.from:contacto@talentusscout.com}")
    private String mailFrom;

    @Value("${talentus.mail.api-key:}")
    private String apiKey;

    @Value("${talentus.mail.branding.logo-url:https://i.imgur.com/9j8oQ2B.png}")
    private String brandingLogoUrl;

    @Value("${talentus.frontend.base-url:https://talentusscout.com}")
    private String frontendBaseUrl;

    public String getBaseUrl() {
        if (frontendBaseUrl == null || frontendBaseUrl.trim().isEmpty()) {
            return "https://talentusscout.com";
        }
        String url = frontendBaseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String certificationInviteTemplate;
    private String emailVerificationTemplate;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/mail/certification-invite.html");
            try (InputStream is = resource.getInputStream()) {
                this.certificationInviteTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.info("Plantilla de correo de certificación cargada exitosamente.");
        } catch (Exception e) {
            log.warn("No se pudo cargar la plantilla HTML de correo desde classpath: {}. Se usará plantilla fallback básica.", e.getMessage());
            this.certificationInviteTemplate = "<p>Hola ##RECIPIENT_NAME##, puedes certificar a ##ATHLETE_NAME## en: <a href='##CERTIFICATION_LINK##'>##CERTIFICATION_LINK##</a></p>";
        }

        try {
            ClassPathResource resource = new ClassPathResource("templates/mail/email-verification.html");
            try (InputStream is = resource.getInputStream()) {
                this.emailVerificationTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.info("Plantilla de correo de verificación cargada exitosamente.");
        } catch (Exception e) {
            log.warn("No se pudo cargar la plantilla HTML de verificación desde classpath: {}. Se usará plantilla fallback básica.", e.getMessage());
            this.emailVerificationTemplate = "<p>Hola ##RECIPIENT_NAME##, verifica tu cuenta en: <a href='##VERIFICATION_LINK##'>##VERIFICATION_LINK##</a></p>";
        }
    }

    /**
     * Método centralizado para el despacho de correos vía Resend API (HTTPS) con fallback simulado.
     */
    private boolean dispatchEmail(String toEmail, String subject, String htmlBody, String mockDetails) {
        if (!mailEnabled || resend == null || apiKey == null || apiKey.trim().isEmpty()) {
            log.info("""
                    \n===================== [MOCK EMAIL SERVICE — RESEND] =====================
                    Para: {}
                    Asunto: {}
                    Remitente: {}
                    {}
                    =========================================================================""",
                    toEmail, subject, mailFrom, mockDetails);
            return true;
        }

        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(mailFrom)
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlBody)
                    .build();

            CreateEmailResponse response = resend.emails().send(params);
            String resendId = (response != null && response.getId() != null) ? response.getId() : "N/A";
            log.info("Correo '{}' despachado con éxito vía Resend API a {}. Resend ID: {}", subject, toEmail, resendId);
            return true;
        } catch (Exception e) {
            log.error("Fallo al despachar correo '{}' a {} vía Resend API: {}", subject, toEmail, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendAdminAlert(String toEmail, String subject, String htmlBody, String mockDetails) {
        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public String getBrandingLogoUrl() {
        return brandingLogoUrl;
    }

    public boolean sendEmailVerification(String toEmail, String recipientName, String verificationLink) {
        Map<String, String> placeholders = Map.of(
                "RECIPIENT_NAME", recipientName != null ? recipientName : "Representante Legal",
                "VERIFICATION_LINK", verificationLink,
                "EXPIRATION_HOURS", "24"
        );

        String subject = "Verifica tu Correo Electrónico — Talentus Scout";
        String htmlBody = renderTemplate(this.emailVerificationTemplate, placeholders);
        String mockDetails = String.format("Destinatario: %s\nEnlace de Verificación: %s\nExpira en: 24 horas", recipientName, verificationLink);

        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendCertificationInvite(String toEmail, String recipientName, String athleteName,
                                          String tutorName, String badgeTypeLabel, String badgeTitle,
                                          String certificationLink, int expirationDays) {
        Map<String, String> placeholders = Map.of(
                "RECIPIENT_NAME", recipientName != null ? recipientName : "Estimado(a) Certificador(a)",
                "ATHLETE_NAME", athleteName,
                "TUTOR_NAME", tutorName != null ? tutorName : "Representante Legal",
                "BADGE_TYPE_LABEL", badgeTypeLabel,
                "BADGE_TITLE", badgeTitle,
                "CERTIFICATION_LINK", certificationLink,
                "EXPIRATION_DAYS", String.valueOf(expirationDays)
        );

        String subject = "Solicitud de Certificación Oficial para " + athleteName + " — Talentus Scout";
        String htmlBody = renderTemplate(this.certificationInviteTemplate, placeholders);
        String mockDetails = String.format("Atleta: %s\nEnlace de Certificación: %s\nExpira en: %d días", athleteName, certificationLink, expirationDays);

        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendPaymentStatusNotification(String toEmail, String tutorName, String athleteName,
                                                String planName, boolean approved, String reason) {
        String subject = approved
                ? "¡Plan Pro Activado para " + athleteName + "! — Talentus Scout"
                : "Actualización sobre tu reporte de Pago Móvil para " + athleteName + " — Talentus Scout";

        String htmlBody = approved
                ? String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #10B981;'>¡Pago Confirmado y Suscripción Activada!</h2>
                      <p>Hola %s,</p>
                      <p>El pago para el plan <strong>%s</strong> de tu atleta <strong>%s</strong> ha sido verificado con éxito.</p>
                      <p>Todas las funciones Pro (Radar de visualizaciones y Hoja de Vida en PDF) ya están activas en tu panel.</p>
                    </div>
                  """, brandingLogoUrl, tutorName, planName, athleteName)
                : String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #EF4444;'>Observación en tu Reporte de Pago</h2>
                      <p>Hola %s,</p>
                      <p>No pudimos conciliar el pago del plan <strong>%s</strong> para <strong>%s</strong>.</p>
                      <p><strong>Motivo:</strong> %s</p>
                      <p>Por favor ingresa a la plataforma para corregir la referencia o comprobante.</p>
                    </div>
                  """, brandingLogoUrl, tutorName, planName, athleteName, reason != null ? reason : "Referencia bancaria no encontrada");

        String mockDetails = String.format("Atleta: %s\nAprobado: %s\nMotivo: %s", athleteName, approved, reason);

        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendLegalVerificationResolutionNotification(String toEmail, String tutorName, String athleteName,
                                                              boolean approved, String reason) {
        String subject = approved
                ? "¡Patria Potestad y Representación Legal Verificada para " + athleteName + "! — Talentus Scout"
                : "Actualización sobre la Documentación Legal de " + athleteName + " — Talentus Scout";

        String htmlBody = approved
                ? String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #10B981;'>¡Patria Potestad Auditada y Aprobada!</h2>
                      <p>Hola %s,</p>
                      <p>El equipo legal y administrativo de Talentus Scout ha verificado exitosamente la documentación de patria potestad para tu atleta <strong>%s</strong>.</p>
                      <p>El perfil de tu atleta ahora cuenta con el distintivo oficial de <strong>Patria Potestad Verificada</strong> y se encuentra plenamente habilitado en el Directorio Oficial de Scouts.</p>
                      <div style='margin-top: 20px;'>
                        <a href='%s/dashboard' style='background: #10B981; color: #0A0F1D; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Ir a Mi Panel</a>
                      </div>
                    </div>
                  """, brandingLogoUrl, tutorName != null ? tutorName : "Representante Legal", athleteName, getBaseUrl())
                : String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #EF4444;'>Observación en la Documentación de Patria Potestad</h2>
                      <p>Hola %s,</p>
                      <p>Hemos revisado los documentos consignados para el atleta <strong>%s</strong> y se requiere una corrección para cumplir con la normativa legal de protección de menores.</p>
                      <p><strong>Motivo / Observación del Administrador:</strong> %s</p>
                      <p>Puedes ingresar a tu panel de representante para cargar una copia clara y legible de la documentación requerida.</p>
                      <div style='margin-top: 20px;'>
                        <a href='%s/dashboard' style='background: #3B82F6; color: #FFFFFF; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Actualizar Documento en Mi Panel</a>
                      </div>
                    </div>
                  """, brandingLogoUrl, tutorName != null ? tutorName : "Representante Legal", athleteName, reason != null ? reason : "Documentación inconsistente o no legible", getBaseUrl());

        String mockDetails = String.format("Atleta: %s\nAprobado: %s\nMotivo: %s", athleteName, approved, reason);

        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendScoutVerificationResolutionNotification(String toEmail, String scoutName,
                                                              boolean approved, String reason) {
        String subject = approved
                ? "¡Acreditación Oficial de Scout Aprobada! — Talentus Scout"
                : "Actualización sobre tu Solicitud de Acreditación de Scout — Talentus Scout";

        String htmlBody = approved
                ? String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #10B981;'>¡Acreditación Oficial Aprobada!</h2>
                      <p>Hola %s,</p>
                      <p>El comité de admisiones de Talentus Scout ha validado exitosamente tus credenciales deportivas y vinculación institucional.</p>
                      <p>Tu cuenta oficial de <strong>Scout / Veedor</strong> ya se encuentra activa con acceso pleno al Directorio Nacional de Talentos Juveniles.</p>
                      <div style='margin-top: 20px;'>
                        <a href='%s/scout/directory' style='background: #10B981; color: #0A0F1D; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Acceder al Directorio de Atletas</a>
                      </div>
                    </div>
                  """, brandingLogoUrl, scoutName != null ? scoutName : "Estimado(a) Scout", getBaseUrl())
                : String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #EF4444;'>Observación en tu Acreditación de Scout</h2>
                      <p>Hola %s,</p>
                      <p>Hemos revisado los recaudos consignados para tu postulación como veedor deportivo y se requiere subsanar información para cumplir con los estándares de salvaguarda juvenil y verificación deportiva.</p>
                      <p><strong>Motivo / Observación del Administrador:</strong> %s</p>
                      <p>Puedes iniciar sesión en la plataforma para cargar una nueva copia legible de tu credencial o carnet institucional actualizado.</p>
                      <div style='margin-top: 20px;'>
                        <a href='%s/login' style='background: #3B82F6; color: #FFFFFF; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Iniciar Sesión y Subsanar Credencial</a>
                      </div>
                    </div>
                  """, brandingLogoUrl, scoutName != null ? scoutName : "Estimado(a) Scout", reason != null ? reason : "Credencial no concluyente o ilegible", getBaseUrl());

        String mockDetails = String.format("Scout: %s\nAprobado: %s\nMotivo: %s", scoutName, approved, reason);

        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendBadgeCertificationResolutionNotification(String toEmail, String tutorName, String athleteName,
                                                                String badgeTitle, String badgeTypeLabel,
                                                                boolean approved, String reason) {
        String subject = approved
                ? "¡Insignia Oficial Verificada para " + athleteName + "! — Talentus Scout"
                : "Actualización sobre la Certificación de Insignia para " + athleteName + " — Talentus Scout";

        String htmlBody = approved
                ? String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #10B981;'>¡Insignia Deportiva Aprobada y Avalada!</h2>
                      <p>Hola %s,</p>
                      <p>Nos complace informarte que la certificación <strong>%s</strong> (%s) para tu atleta <strong>%s</strong> ha sido verificada y aprobada oficialmente.</p>
                      <p>La insignia ya luce visible en su Cédula Deportiva pública y en el radar de los scouts de clubes profesionales.</p>
                      <div style='margin-top: 20px;'>
                        <a href='%s/dashboard' style='background: #10B981; color: #0A0F1D; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Ver Cédula en Mi Panel</a>
                      </div>
                    </div>
                  """, brandingLogoUrl, tutorName != null ? tutorName : "Representante Legal", badgeTitle, badgeTypeLabel, athleteName, getBaseUrl())
                : String.format("""
                    <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                      <div style='text-align: center; margin-bottom: 20px;'>
                        <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                      </div>
                      <h2 style='color: #EF4444;'>Observación en la Solicitud de Insignia</h2>
                      <p>Hola %s,</p>
                      <p>La solicitud de certificación <strong>%s</strong> (%s) para el atleta <strong>%s</strong> ha sido revisada con observaciones.</p>
                      <p><strong>Motivo / Observación:</strong> %s</p>
                      <p>Puedes ingresar a tu panel de tutor para generar una nueva solicitud con los recaudos o contactos actualizados.</p>
                      <div style='margin-top: 20px;'>
                        <a href='%s/dashboard' style='background: #3B82F6; color: #FFFFFF; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Ir a Mi Panel de Tutor</a>
                      </div>
                    </div>
                  """, brandingLogoUrl, tutorName != null ? tutorName : "Representante Legal", badgeTitle, badgeTypeLabel, athleteName, reason != null ? reason : "Documento probatorio no concluyente", getBaseUrl());

        String mockDetails = String.format("Atleta: %s\nInsignia: %s\nAprobado: %s\nMotivo: %s", athleteName, badgeTitle, approved, reason);

        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendScoutClubRevocationNotification(String toEmail, String scoutName, String clubName, String reason) {
        String subject = "Actualización sobre tu Acreditación de Scout — Talentus Scout";
        String htmlBody = String.format("""
                <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                  <div style='text-align: center; margin-bottom: 20px;'>
                    <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                  </div>
                  <h2 style='color: #F59E0B;'>Subsanación Requerida: Baja de Organización Deportiva</h2>
                  <p>Hola %s,</p>
                  <p>Te informamos que la organización deportiva <strong>%s</strong> a la que estabas vinculado ha sido dada de baja en nuestra plataforma.</p>
                  <p>Para garantizar los estándares de salvaguarda y protección de menores (LOPNNA / FIFA), tu perfil de scout requiere una actualización de credenciales y club activo para continuar con acceso al Directorio de Talentos Juveniles.</p>
                  <p><strong>Detalle:</strong> %s</p>
                  <div style='margin-top: 20px;'>
                    <a href='%s/login' style='background: #3B82F6; color: #FFFFFF; font-weight: bold; padding: 10px 20px; border-radius: 6px; text-decoration: none;'>Iniciar Sesión y Actualizar Perfil</a>
                  </div>
                </div>
              """, brandingLogoUrl, scoutName != null ? scoutName : "Estimado(a) Scout", clubName, reason != null ? reason : "Club dado de baja por la administración.", getBaseUrl());

        String mockDetails = String.format("Scout: %s\nClub: %s\nMotivo: %s", scoutName, clubName, reason);
        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    public boolean sendPasswordResetEmail(String toEmail, String recipientName, String resetLink) {
        String subject = "Recuperación de Contraseña — Talentus Scout";
        String htmlBody = String.format("""
                <div style='font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 32px 24px; border-radius: 12px; max-width: 600px; margin: 0 auto; border: 1px solid rgba(255, 255, 255, 0.08);'>
                  <div style='text-align: center; margin-bottom: 24px;'>
                    <img src='%s' alt='Talentus Scout' style='max-height: 48px; display: inline-block;' />
                  </div>
                  <h2 style='color: #10B981; font-size: 20px; font-weight: 700; margin-top: 0; margin-bottom: 16px;'>Solicitud de Restablecimiento de Contraseña</h2>
                  <p style='color: #E2E8F0; font-size: 15px; line-height: 1.6; margin-bottom: 16px;'>Hola %s,</p>
                  <p style='color: #CBD5E1; font-size: 14px; line-height: 1.6; margin-bottom: 20px;'>
                    Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en Talentus Scout. Para crear una nueva clave, haz clic en el siguiente botón:
                  </p>
                  <div style='text-align: center; margin: 28px 0;'>
                    <a href='%s' style='background: #10B981; color: #0A0F1D; font-weight: 700; font-size: 15px; padding: 14px 28px; border-radius: 8px; text-decoration: none; display: inline-block; box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);'>
                      Restablecer mi Contraseña
                    </a>
                  </div>
                  <p style='color: #94A3B8; font-size: 13px; line-height: 1.5; margin-bottom: 8px;'>
                    ⏱️ Este enlace es de un solo uso y expirará en <strong>30 minutos</strong> por razones de seguridad.
                  </p>
                  <p style='color: #64748B; font-size: 12px; line-height: 1.5; margin-bottom: 0;'>
                    Si no realizaste esta solicitud, puedes ignorar este correo de forma segura; tu contraseña actual no sufrirá ningún cambio.
                  </p>
                </div>
              """, brandingLogoUrl, recipientName != null ? recipientName : "Usuario", resetLink);

        String mockDetails = String.format("Destinatario: %s\nEnlace de Restablecimiento: %s\nExpira en: 30 minutos", recipientName, resetLink);
        return dispatchEmail(toEmail, subject, htmlBody, mockDetails);
    }

    private String renderTemplate(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("##" + entry.getKey() + "##", entry.getValue());
        }
        return result;
    }
}
