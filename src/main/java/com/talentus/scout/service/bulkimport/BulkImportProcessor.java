package com.talentus.scout.service.bulkimport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.*;
import com.talentus.scout.repository.*;
import com.talentus.scout.service.SlugService;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowErrorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportProcessor {

    private final BulkImportJobRepository jobRepository;
    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final TutorActivationTokenRepository tokenRepository;
    private final SlugService slugService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Async
    public void processImportJob(UUID jobId, List<BulkImportRowDto> rows) {
        log.info("Iniciando procesamiento de BulkImportJob [{}] con {} filas", jobId, rows.size());
        Optional<BulkImportJob> optJob = jobRepository.findById(jobId);
        if (optJob.isEmpty()) {
            log.error("BulkImportJob no encontrado: {}", jobId);
            return;
        }

        BulkImportJob job = optJob.get();
        job.setStatus(BulkImportStatus.PROCESSING);
        job.setTotalRows(rows.size());
        job.setProcessedRows(0);
        job.setSuccessfulRows(0);
        job.setFailedRows(0);
        job = jobRepository.save(job);

        List<BulkImportRowErrorDto> warnings = new ArrayList<>();
        Map<String, User> tutorCache = new HashMap<>(); // email -> User
        Map<String, List<String>> tutorToAthletes = new HashMap<>(); // email -> List of athlete names
        Map<String, String> tutorTokens = new HashMap<>(); // email -> rawToken

        for (BulkImportRowDto row : rows) {
            job.setProcessedRows(job.getProcessedRows() + 1);

            try {
                // 1. Validaciones básicas de campos obligatorios
                if (row.getPrimerNombre() == null || row.getPrimerApellido() == null ||
                        row.getFechaNacimiento() == null || row.getPosicionPrincipal() == null ||
                        row.getCorreoTutor() == null || row.getNombreCompletoTutor() == null) {

                    warnings.add(BulkImportRowErrorDto.builder()
                            .rowNumber(row.getRowNumber())
                            .athleteName(row.getPrimerNombre() + " " + row.getPrimerApellido())
                            .reason("Faltan campos obligatorios requeridos (nombre, apellido, fecha_nacimiento, posicion o correo_tutor)")
                            .actionTaken("FILA_OMITIDA")
                            .build());
                    job.setFailedRows(job.getFailedRows() + 1);
                    continue;
                }

                // 2. Control de duplicados de atleta
                boolean duplicateFound = false;
                if (row.getCedulaIdentidadAtleta() != null && !row.getCedulaIdentidadAtleta().isBlank()) {
                    if (athleteRepository.existsByIdentityDocumentNumberAndDeletedAtIsNull(row.getCedulaIdentidadAtleta().trim())) {
                        warnings.add(BulkImportRowErrorDto.builder()
                                .rowNumber(row.getRowNumber())
                                .athleteName(row.getPrimerNombre() + " " + row.getPrimerApellido())
                                .reason("El atleta con cédula/DNI " + row.getCedulaIdentidadAtleta() + " ya se encuentra registrado en el sistema")
                                .actionTaken("OMITIDO_DUPLICADO")
                                .build());
                        duplicateFound = true;
                    }
                }

                if (duplicateFound) {
                    job.setFailedRows(job.getFailedRows() + 1);
                    continue;
                }

                // 3. Crear o reutilizar tutor
                String tutorEmail = row.getCorreoTutor().trim().toLowerCase();
                User tutor = tutorCache.get(tutorEmail);
                boolean isNewTutor = false;

                if (tutor == null) {
                    Optional<User> existingUserOpt = userRepository.findByEmail(tutorEmail);
                    if (existingUserOpt.isPresent()) {
                        tutor = existingUserOpt.get();
                        // Si estaba eliminado lógicamente pero el scout lo re-inscribe
                        if (tutor.getDeletedAt() != null) {
                            tutor.setDeletedAt(null);
                            tutor.setStatus(AccountStatus.ACTIVE);
                            userRepository.save(tutor);
                        }
                    } else {
                        // Crear nuevo tutor con contraseña temporal aleatoria encriptada
                        String tempPassword = UUID.randomUUID().toString();
                        tutor = User.builder()
                                .email(tutorEmail)
                                .fullName(row.getNombreCompletoTutor())
                                .phoneNumber(row.getTelefonoTutor() != null ? row.getTelefonoTutor() : "")
                                .passwordHash(passwordEncoder.encode(tempPassword))
                                .role(UserRole.TUTOR)
                                .status(AccountStatus.ACTIVE)
                                .build();
                        tutor = userRepository.save(tutor);
                        isNewTutor = true;
                    }
                    tutorCache.put(tutorEmail, tutor);
                }

                // Generar token de activación para el tutor si es nuevo o no tiene token activo
                if (isNewTutor && !tutorTokens.containsKey(tutorEmail)) {
                    String rawToken = UUID.randomUUID().toString();
                    TutorActivationToken activationToken = TutorActivationToken.builder()
                            .tutor(tutor)
                            .tokenHash(rawToken)
                            .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                            .build();
                    tokenRepository.save(activationToken);
                    tutorTokens.put(tutorEmail, rawToken);
                }

                // 4. Crear el Atleta
                String slug = slugService.generateUniqueSlug(row.getPrimerNombre(), row.getPrimerApellido());
                Athlete athlete = Athlete.builder()
                        .tutor(tutor)
                        .firstName(row.getPrimerNombre())
                        .lastName(row.getPrimerApellido() + (row.getSegundoApellido() != null ? " " + row.getSegundoApellido() : ""))
                        .birthDate(row.getFechaNacimiento())
                        .gender(row.getGenero() != null ? row.getGenero() : "MALE")
                        .primaryPosition(row.getPosicionPrincipal())
                        .secondaryPosition(row.getPosicionSecundaria())
                        .preferredFoot(row.getPieDominante() != null ? row.getPieDominante() : "RIGHT")
                        .nationality(row.getNacionalidad() != null ? row.getNacionalidad() : "Venezolana")
                        .heightCm(row.getAlturaCm())
                        .weightKg(row.getPesoKg())
                        .identityDocumentNumber(row.getCedulaIdentidadAtleta())
                        .currentClub(job.getClub())
                        .slug(slug)
                        .isPublic(true)
                        .legalVerificationStatus(LegalVerificationStatus.PENDING_REVIEW)
                        .build();

                athleteRepository.save(athlete);
                job.setSuccessfulRows(job.getSuccessfulRows() + 1);

                String athleteFullName = row.getPrimerNombre() + " " + row.getPrimerApellido();
                tutorToAthletes.computeIfAbsent(tutorEmail, k -> new ArrayList<>()).add(athleteFullName);

            } catch (Exception ex) {
                log.error("Error procesando fila {}: {}", row.getRowNumber(), ex.getMessage(), ex);
                warnings.add(BulkImportRowErrorDto.builder()
                        .rowNumber(row.getRowNumber())
                        .athleteName(row.getPrimerNombre() + " " + row.getPrimerApellido())
                        .reason("Error interno: " + ex.getMessage())
                        .actionTaken("FILA_CON_ERROR")
                        .build());
                job.setFailedRows(job.getFailedRows() + 1);
            }
        }

        // 5. Enviar correos de notificación a los tutores involucrados
        for (Map.Entry<String, List<String>> entry : tutorToAthletes.entrySet()) {
            String email = entry.getKey();
            List<String> athleteList = entry.getValue();
            User tutor = tutorCache.get(email);
            String rawToken = tutorTokens.get(email);
            if (rawToken == null) {
                // Tutor previo existente: generar token para notificación o acceso
                rawToken = UUID.randomUUID().toString();
                TutorActivationToken activationToken = TutorActivationToken.builder()
                        .tutor(tutor)
                        .tokenHash(rawToken)
                        .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                        .build();
                tokenRepository.save(activationToken);
            }

            String activationLink = emailService.getBaseUrl() + "/auth/activar-tutor?token=" + rawToken;
            String rejectLink = emailService.getBaseUrl() + "/auth/rechazar-tutor?token=" + rawToken;
            String athleteNames = String.join(", ", athleteList);

            try {
                emailService.sendTutorBulkImportInvite(
                        email,
                        tutor.getFullName(),
                        athleteNames,
                        job.getOrganizationName(),
                        activationLink,
                        rejectLink
                );
            } catch (Exception ex) {
                log.error("Error al enviar email de activación a tutor {}: {}", email, ex.getMessage());
            }
        }

        // 6. Finalizar el Job
        job.setStatus(BulkImportStatus.COMPLETED);
        try {
            job.setErrorReportJson(objectMapper.writeValueAsString(warnings));
        } catch (Exception ignored) {
        }
        job.setErrorSummary(String.format("Carga completada: %d exitosos, %d omitidos/fallidos",
                job.getSuccessfulRows(), job.getFailedRows()));
        jobRepository.save(job);

        log.info("BulkImportJob [{}] completado exitosamente con {} filas exitosas y {} advertencias",
                jobId, job.getSuccessfulRows(), warnings.size());
    }
}
