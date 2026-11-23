package com.talentus.scout.service.bulkimport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.*;
import com.talentus.scout.exception.DuplicateAthleteDocumentException;
import com.talentus.scout.repository.*;
import com.talentus.scout.service.SlugService;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.util.AthleteFieldNormalizer;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowErrorDto;
import com.talentus.scout.web.dto.bulkimport.ScoutIndividualAthleteResponse;
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
        // Capturar el club hidratado antes de merge/save fuera de sesión JPA
        ClubOrganization resolvedClub = job.getClub();
        job.setStatus(BulkImportStatus.PROCESSING);
        job.setTotalRows(rows.size());
        job.setProcessedRows(0);
        job.setSuccessfulRows(0);
        job.setFailedRows(0);
        job = jobRepository.save(job);

        try {
        List<BulkImportRowErrorDto> warnings = new ArrayList<>();
        Map<String, User> tutorCache = new HashMap<>(); // email -> User
        Map<String, List<String>> tutorToAthletes = new HashMap<>(); // email -> List of athlete names
        Map<String, String> tutorTokens = new HashMap<>(); // email -> rawToken

        for (BulkImportRowDto row : rows) {
            job.setProcessedRows(job.getProcessedRows() + 1);

            try {
                // 0. Validaciones de formato y catálogo detectadas durante el parseo
                if (row.getValidationError() != null && !row.getValidationError().isBlank()) {
                    warnings.add(BulkImportRowErrorDto.builder()
                            .rowNumber(row.getRowNumber())
                            .athleteName((row.getPrimerNombre() != null ? row.getPrimerNombre() : "") + " " + (row.getPrimerApellido() != null ? row.getPrimerApellido() : ""))
                            .reason(row.getValidationError())
                            .actionTaken("FILA_OMITIDA")
                            .build());
                    job.setFailedRows(job.getFailedRows() + 1);
                    continue;
                }

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

                // 1.1 Validación de fecha de nacimiento y rango de edad formativo (4 a 21 años)
                try {
                    AthleteFieldNormalizer.validateBirthDateAndFormativeAge(row.getFechaNacimiento());
                } catch (IllegalArgumentException ex) {
                    warnings.add(BulkImportRowErrorDto.builder()
                            .rowNumber(row.getRowNumber())
                            .athleteName(row.getPrimerNombre() + " " + row.getPrimerApellido())
                            .reason(ex.getMessage())
                            .actionTaken("FILA_OMITIDA")
                            .build());
                    job.setFailedRows(job.getFailedRows() + 1);
                    continue;
                }

                // 2. Control de duplicados de atleta
                if (isDuplicateAthleteDocument(row.getCedulaIdentidadAtleta())) {
                    warnings.add(BulkImportRowErrorDto.builder()
                            .rowNumber(row.getRowNumber())
                            .athleteName(row.getPrimerNombre() + " " + row.getPrimerApellido())
                            .reason("El atleta con cédula/DNI " + row.getCedulaIdentidadAtleta() + " ya se encuentra registrado en el sistema")
                            .actionTaken("OMITIDO_DUPLICADO")
                            .build());
                    job.setFailedRows(job.getFailedRows() + 1);
                    continue;
                }

                // 3. Crear o reutilizar tutor
                String tutorEmail = row.getCorreoTutor().trim().toLowerCase();
                User tutor = tutorCache.get(tutorEmail);
                boolean isNewTutor = false;

                if (tutor == null) {
                    TutorResolution resolution = resolveOrCreateTutor(row, resolvedClub);
                    tutor = resolution.tutor();
                    isNewTutor = resolution.isNewTutor();
                    tutorCache.put(tutorEmail, tutor);
                }

                // Generar token de activación para el tutor si es nuevo o no tiene token activo
                if (isNewTutor && !tutorTokens.containsKey(tutorEmail)) {
                    tutorTokens.put(tutorEmail, createActivationToken(tutor));
                }

                // 4. Crear el Atleta
                createAndSaveAthlete(row, tutor, resolvedClub);
                job.setSuccessfulRows(job.getSuccessfulRows() + 1);

                String athleteFullName = joinNames(row.getPrimerNombre(), row.getSegundoNombre())
                        + " " + joinNames(row.getPrimerApellido(), row.getSegundoApellido());
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
                rawToken = createActivationToken(tutor);
            }

            String athleteNames = String.join(", ", athleteList);
            sendTutorInviteEmail(email, tutor.getFullName(), athleteNames, job.getOrganizationName(), rawToken);
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
        } catch (Exception fatalEx) {
            log.error("Error fatal procesando BulkImportJob [{}]: {}", jobId, fatalEx.getMessage(), fatalEx);
            job.setStatus(BulkImportStatus.FAILED);
            job.setErrorSummary("Error procesando el lote: " + fatalEx.getMessage());
            jobRepository.save(job);
        }
    }

    @Transactional
    public ScoutIndividualAthleteResponse registerIndividualAthlete(
            BulkImportRowDto row,
            ClubOrganization resolvedClub,
            String organizationName
    ) {
        AthleteFieldNormalizer.validateBirthDateAndFormativeAge(row.getFechaNacimiento());

        if (isDuplicateAthleteDocument(row.getCedulaIdentidadAtleta())) {
            throw new DuplicateAthleteDocumentException(
                    "El atleta con cédula/DNI " + row.getCedulaIdentidadAtleta().trim() + " ya se encuentra registrado en el sistema"
            );
        }

        TutorResolution tutorResolution = resolveOrCreateTutor(row, resolvedClub);
        User tutor = tutorResolution.tutor();
        String rawToken = createActivationToken(tutor);
        Athlete athlete = createAndSaveAthlete(row, tutor, resolvedClub);

        String athleteFullName = athlete.getFirstName() + " " + athlete.getLastName();
        sendTutorInviteEmail(tutor.getEmail(), tutor.getFullName(), athleteFullName, organizationName, rawToken);

        return ScoutIndividualAthleteResponse.builder()
                .athleteId(athlete.getId())
                .slug(athlete.getSlug())
                .athleteFullName(athleteFullName)
                .clubName(resolvedClub != null ? resolvedClub.getName() : organizationName)
                .tutorEmail(tutor.getEmail())
                .isNewTutor(tutorResolution.isNewTutor())
                .legalVerificationStatus(athlete.getLegalVerificationStatus().name())
                .build();
    }

    private boolean isDuplicateAthleteDocument(String cedula) {
        if (cedula == null || cedula.isBlank()) {
            return false;
        }
        return athleteRepository.existsByIdentityDocumentNumberAndDeletedAtIsNull(cedula.trim());
    }

    private TutorResolution resolveOrCreateTutor(BulkImportRowDto row, ClubOrganization resolvedClub) {
        String tutorEmail = row.getCorreoTutor().trim().toLowerCase();
        Optional<User> existingUserOpt = userRepository.findByEmail(tutorEmail);
        if (existingUserOpt.isPresent()) {
            User tutor = existingUserOpt.get();
            if (tutor.getDeletedAt() != null) {
                tutor.setDeletedAt(null);
                tutor.setStatus(AccountStatus.ACTIVE);
                userRepository.save(tutor);
            }
            return new TutorResolution(tutor, false);
        }

        String tempPassword = UUID.randomUUID().toString();
        String tutorCountry = (resolvedClub != null && resolvedClub.getCountry() != null && !resolvedClub.getCountry().isBlank())
                ? resolvedClub.getCountry()
                : AthleteFieldNormalizer.resolveAuthorizedCountryOrDefault(row.getNacionalidad());
        User tutor = User.builder()
                .email(tutorEmail)
                .fullName(row.getNombreCompletoTutor().trim())
                .phoneNumber(row.getTelefonoTutor() != null ? row.getTelefonoTutor().trim() : "")
                .country(tutorCountry)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        return new TutorResolution(userRepository.save(tutor), true);
    }

    private String createActivationToken(User tutor) {
        String rawToken = UUID.randomUUID().toString();
        TutorActivationToken activationToken = TutorActivationToken.builder()
                .tutor(tutor)
                .tokenHash(rawToken)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        tokenRepository.save(activationToken);
        return rawToken;
    }

    private Athlete createAndSaveAthlete(BulkImportRowDto row, User tutor, ClubOrganization resolvedClub) {
        String firstName = joinNames(row.getPrimerNombre(), row.getSegundoNombre());
        String lastName = joinNames(row.getPrimerApellido(), row.getSegundoApellido());
        String slug = slugService.generateUniqueSlug(firstName, lastName);

        Athlete athlete = Athlete.builder()
                .tutor(tutor)
                .firstName(firstName)
                .lastName(lastName)
                .birthDate(row.getFechaNacimiento())
                .gender(row.getGenero() != null ? row.getGenero() : "MALE")
                .primaryPosition(row.getPosicionPrincipal())
                .secondaryPosition(row.getPosicionSecundaria())
                .preferredFoot(row.getPieDominante() != null ? row.getPieDominante() : "DERECHO")
                .nationality(row.getNacionalidad() != null ? row.getNacionalidad() : "Venezuela")
                .heightCm(row.getAlturaCm())
                .weightKg(row.getPesoKg())
                .identityDocumentNumber(row.getCedulaIdentidadAtleta() != null ? row.getCedulaIdentidadAtleta().trim() : null)
                .currentClub(resolvedClub)
                .slug(slug)
                .isPublic(true)
                .legalVerificationStatus(LegalVerificationStatus.PENDING_REVIEW)
                .build();

        return athleteRepository.save(athlete);
    }

    private void sendTutorInviteEmail(
            String email,
            String tutorFullName,
            String athleteNames,
            String organizationName,
            String rawToken
    ) {
        String activationLink = emailService.getBaseUrl() + "/auth/activar-tutor?token=" + rawToken;
        String rejectLink = emailService.getBaseUrl() + "/auth/rechazar-tutor?token=" + rawToken;
        try {
            emailService.sendTutorBulkImportInvite(
                    email,
                    tutorFullName,
                    athleteNames,
                    organizationName,
                    activationLink,
                    rejectLink
            );
        } catch (Exception ex) {
            log.error("Error al enviar email de activación a tutor {}: {}", email, ex.getMessage());
        }
    }

    private String joinNames(String primary, String secondary) {
        String p = primary != null ? primary.trim() : "";
        String s = secondary != null ? secondary.trim() : "";
        return s.isEmpty() ? p : p + " " + s;
    }

    private record TutorResolution(User tutor, boolean isNewTutor) {
    }
}
