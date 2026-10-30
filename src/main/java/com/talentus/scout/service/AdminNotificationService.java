package com.talentus.scout.service;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminNotificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${talentus.mail.admin-fallback-email:admin@talentusscout.com}")
    private String adminFallbackEmail;

    @Value("${talentus.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public AdminNotificationService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public List<String> getActiveAdminEmails() {
        try {
            List<User> activeAdmins = userRepository.findByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE);
            if (activeAdmins != null && !activeAdmins.isEmpty()) {
                List<String> emails = activeAdmins.stream()
                        .map(User::getEmail)
                        .filter(e -> e != null && !e.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
                if (!emails.isEmpty()) {
                    return emails;
                }
            }
        } catch (Exception e) {
            log.warn("Fallo al consultar administradores activos en BD: {}. Usando correo fallback.", e.getMessage());
        }
        return List.of(adminFallbackEmail);
    }

    @Async
    public void notifyNewTutorRegistered(User tutor, TutorLegalConsent consent) {
        if (tutor == null) return;

        String subject = "[ALERTA ADMIN] Nuevo Representante Registrado — Requiere Verificación de Documento";
        String auditUrl = frontendBaseUrl + "/admin/verificacion-legal";

        String docType = (consent != null && consent.getIdDocumentType() != null) ? consent.getIdDocumentType() : "Cédula";
        String docNumber = (consent != null && consent.getIdDocumentNumber() != null) ? consent.getIdDocumentNumber() : "No especificado";
        String docFileStatus = (consent != null && consent.getIdDocumentFileUrl() != null) ? "Documento Adjunto en Plataforma" : "Pendiente de Carga";

        String htmlBody = String.format("""
                <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                  <div style='text-align: center; margin-bottom: 20px;'>
                    <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                  </div>
                  <div style='background: rgba(16, 185, 129, 0.15); border: 1px solid #10B981; padding: 12px; border-radius: 8px; margin-bottom: 20px;'>
                    <strong style='color: #10B981; font-size: 14px;'>NUEVO REPRESENTANTE / TUTOR LEGAL REGISTRADO</strong>
                  </div>
                  <p>Estimado(a) Administrador(a),</p>
                  <p>Se ha registrado un nuevo tutor en la plataforma bajo la normativa LOPNNA Art. 65:</p>
                  <table style='width: 100%%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;'>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Nombre Completo:</td><td style='padding: 6px 0; font-weight: bold;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Correo Electrónico:</td><td style='padding: 6px 0; font-weight: bold;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Teléfono:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Ubicación:</td><td style='padding: 6px 0;'>%s, %s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Documento Identidad:</td><td style='padding: 6px 0;'>%s %s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Archivo de Documento:</td><td style='padding: 6px 0; color: #10B981;'>%s</td></tr>
                  </table>
                  <div style='margin-top: 24px;'>
                    <a href='%s' style='background: #10B981; color: #0A0F1D; font-weight: bold; padding: 12px 24px; border-radius: 6px; text-decoration: none; display: inline-block;'>Auditar Documento de Representante</a>
                  </div>
                </div>
              """,
                emailService.getBrandingLogoUrl(),
                tutor.getFullName(),
                tutor.getEmail(),
                tutor.getPhoneNumber() != null ? tutor.getPhoneNumber() : "No provisto",
                tutor.getState() != null ? tutor.getState() : "No especificado",
                tutor.getCountry() != null ? tutor.getCountry() : "Venezuela",
                docType,
                docNumber,
                docFileStatus,
                auditUrl
        );

        String mockDetails = String.format("Tutor: %s (%s)\nUbicación: %s\nEnlace: %s", tutor.getFullName(), tutor.getEmail(), tutor.getState(), auditUrl);

        for (String adminEmail : getActiveAdminEmails()) {
            emailService.sendAdminAlert(adminEmail, subject, htmlBody, mockDetails);
        }
        log.info("Alerta de nuevo tutor {} despachada a administradores.", tutor.getEmail());
    }

    @Async
    public void notifyNewAthleteRegistered(Athlete athlete) {
        if (athlete == null) return;

        String subject = "[ALERTA ADMIN] Nuevo Atleta Inscrito — Patria Potestad Pendiente de Auditoría";
        String auditUrl = frontendBaseUrl + "/admin/verificacion-legal";

        String athleteName = athlete.getFirstName() + " " + athlete.getLastName();
        String tutorName = athlete.getTutor() != null ? athlete.getTutor().getFullName() : "Representante Legal";
        String tutorEmail = athlete.getTutor() != null ? athlete.getTutor().getEmail() : "N/A";
        String clubName = athlete.getCurrentClub() != null ? athlete.getCurrentClub().getName() : "Independiente / Sin afiliación";

        String htmlBody = String.format("""
                <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                  <div style='text-align: center; margin-bottom: 20px;'>
                    <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                  </div>
                  <div style='background: rgba(56, 189, 248, 0.15); border: 1px solid #38BDF8; padding: 12px; border-radius: 8px; margin-bottom: 20px;'>
                    <strong style='color: #38BDF8; font-size: 14px;'>NUEVO ATLETA REGISTRADO — AUDITORÍA DE PATRIA POTESTAD</strong>
                  </div>
                  <p>Estimado(a) Administrador(a),</p>
                  <p>Se ha inscrito un nuevo atleta juvenil en la plataforma. Requiere revisión para habilitar su visibilidad en el radar de reclutamiento:</p>
                  <table style='width: 100%%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;'>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Atleta:</td><td style='padding: 6px 0; font-weight: bold;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Fecha de Nacimiento:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Posición:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Club / Academia:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Representante Responsable:</td><td style='padding: 6px 0;'>%s (%s)</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Estatus Legal:</td><td style='padding: 6px 0; color: #F59E0B;'>PENDIENTE DE REVISIÓN</td></tr>
                  </table>
                  <div style='margin-top: 24px;'>
                    <a href='%s' style='background: #38BDF8; color: #0A0F1D; font-weight: bold; padding: 12px 24px; border-radius: 6px; text-decoration: none; display: inline-block;'>Auditar Patria Potestad del Atleta</a>
                  </div>
                </div>
              """,
                emailService.getBrandingLogoUrl(),
                athleteName,
                athlete.getBirthDate() != null ? athlete.getBirthDate().toString() : "No especificada",
                athlete.getPrimaryPosition() != null ? athlete.getPrimaryPosition() : "No definida",
                clubName,
                tutorName,
                tutorEmail,
                auditUrl
        );

        String mockDetails = String.format("Atleta: %s\nTutor: %s (%s)\nClub: %s\nEnlace: %s", athleteName, tutorName, tutorEmail, clubName, auditUrl);

        for (String adminEmail : getActiveAdminEmails()) {
            emailService.sendAdminAlert(adminEmail, subject, htmlBody, mockDetails);
        }
        log.info("Alerta de nuevo atleta {} despachada a administradores.", athleteName);
    }

    @Async
    public void notifyNewCustomClubCreated(ClubOrganization club, String creatorEmail) {
        if (club == null) return;

        String subject = "[ALERTA ADMIN] Nueva Organización Registrada por Comunidad — Pendiente de Homologación";
        String auditUrl = frontendBaseUrl + "/admin/clubes";

        String htmlBody = String.format("""
                <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                  <div style='text-align: center; margin-bottom: 20px;'>
                    <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                  </div>
                  <div style='background: rgba(245, 158, 11, 0.15); border: 1px solid #F59E0B; padding: 12px; border-radius: 8px; margin-bottom: 20px;'>
                    <strong style='color: #F59E0B; font-size: 14px;'>NUEVA ORGANIZACIÓN DEPORTIVA REGISTRADA POR LA COMUNIDAD</strong>
                  </div>
                  <p>Estimado(a) Administrador(a),</p>
                  <p>Un usuario ha dado de alta una nueva organización deportiva no oficial mediante la opción 'Otro'. Requiere auditoría y eventual homologación oficial:</p>
                  <table style='width: 100%%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;'>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Organización:</td><td style='padding: 6px 0; font-weight: bold;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Ubicación:</td><td style='padding: 6px 0;'>%s, %s, %s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Registrado por:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Estatus Actual:</td><td style='padding: 6px 0; color: #F59E0B;'>Comunitario (verified_official = false)</td></tr>
                  </table>
                  <div style='margin-top: 24px;'>
                    <a href='%s' style='background: #F59E0B; color: #0A0F1D; font-weight: bold; padding: 12px 24px; border-radius: 6px; text-decoration: none; display: inline-block;'>Verificar y Homologar Club</a>
                  </div>
                </div>
              """,
                emailService.getBrandingLogoUrl(),
                club.getName(),
                club.getCity(),
                club.getState(),
                club.getCountry(),
                creatorEmail != null ? creatorEmail : "Comunidad de usuarios",
                auditUrl
        );

        String mockDetails = String.format("Club: %s\nSede: %s, %s\nCreador: %s\nEnlace: %s", club.getName(), club.getCity(), club.getState(), creatorEmail, auditUrl);

        for (String adminEmail : getActiveAdminEmails()) {
            emailService.sendAdminAlert(adminEmail, subject, htmlBody, mockDetails);
        }
        log.info("Alerta de nuevo club comunitario {} despachada a administradores.", club.getName());
    }

    @Async
    public void notifyNewScoutRegistered(ScoutProfile scout) {
        if (scout == null) return;

        String subject = "[ALERTA ADMIN] Nueva Solicitud de Acreditación de Scout — Requiere Auditoría";
        String auditUrl = frontendBaseUrl + "/admin/scouts";

        String scoutName = scout.getUser() != null ? scout.getUser().getFullName() : "Scout / Reclutador";
        String scoutEmail = scout.getUser() != null ? scout.getUser().getEmail() : "N/A";
        String scoutPhone = (scout.getUser() != null && scout.getUser().getPhoneNumber() != null) ? scout.getUser().getPhoneNumber() : "No indicado";

        String htmlBody = String.format("""
                <div style='font-family: sans-serif; background: #0A0F1D; color: #F8FAFC; padding: 24px; border-radius: 12px;'>
                  <div style='text-align: center; margin-bottom: 20px;'>
                    <img src='%s' alt='Talentus Scout' style='max-height: 44px; display: inline-block;' />
                  </div>
                  <div style='background: rgba(192, 132, 252, 0.15); border: 1px solid #C084FC; padding: 12px; border-radius: 8px; margin-bottom: 20px;'>
                    <strong style='color: #C084FC; font-size: 14px;'>NUEVA SOLICITUD DE SCOUT / VEEDOR EN ESPERA DE ACREDITACIÓN</strong>
                  </div>
                  <p>Estimado(a) Administrador(a),</p>
                  <p>Un reclutador deportivo ha completado su registro consignando credenciales profesionales para acceder al Directorio de Talentos Juveniles:</p>
                  <table style='width: 100%%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;'>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Nombre:</td><td style='padding: 6px 0; font-weight: bold;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Correo:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Teléfono:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Organización:</td><td style='padding: 6px 0; font-weight: bold;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Rol Deportivo:</td><td style='padding: 6px 0;'>%s</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>Credencial Deportiva:</td><td style='padding: 6px 0; color: #10B981;'>Documento Consignado</td></tr>
                    <tr><td style='padding: 6px 0; color: #94A3B8;'>LinkedIn:</td><td style='padding: 6px 0;'>%s</td></tr>
                  </table>
                  <div style='margin-top: 24px;'>
                    <a href='%s' style='background: #C084FC; color: #0A0F1D; font-weight: bold; padding: 12px 24px; border-radius: 6px; text-decoration: none; display: inline-block;'>Auditar Credencial de Scout</a>
                  </div>
                </div>
              """,
                emailService.getBrandingLogoUrl(),
                scoutName,
                scoutEmail,
                scoutPhone,
                scout.getOrganizationName(),
                scout.getScoutRole(),
                scout.getLinkedinUrl() != null ? scout.getLinkedinUrl() : "No indicado",
                auditUrl
        );

        String mockDetails = String.format("Scout: %s (%s)\nOrganización: %s\nRol: %s\nEnlace: %s", scoutName, scoutEmail, scout.getOrganizationName(), scout.getScoutRole(), auditUrl);

        for (String adminEmail : getActiveAdminEmails()) {
            emailService.sendAdminAlert(adminEmail, subject, htmlBody, mockDetails);
        }
        log.info("Alerta de nuevo scout {} despachada a administradores.", scoutEmail);
    }
}
