package com.talentus.scout.service.bulkimport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.BulkImportJob;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.BulkImportSourceType;
import com.talentus.scout.domain.enums.BulkImportStatus;
import com.talentus.scout.repository.BulkImportJobRepository;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.util.AthleteFieldNormalizer;
import com.talentus.scout.web.dto.bulkimport.BulkImportJobResponseDto;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowErrorDto;
import com.talentus.scout.web.dto.bulkimport.ScoutIndividualAthleteRequest;
import com.talentus.scout.web.dto.bulkimport.ScoutIndividualAthleteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService {

    private final BulkImportJobRepository jobRepository;
    private final UserRepository userRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final ClubOrganizationRepository clubOrganizationRepository;
    private final BulkImportParser parser;
    private final GoogleSheetsService googleSheetsService;
    private final BulkImportProcessor processor;
    private final ObjectMapper objectMapper;

    @Transactional
    public BulkImportJobResponseDto startFileUploadJob(UUID scoutUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar un archivo válido para importar");
        }

        ScoutContext context = validateAndResolveScoutContext(scoutUserId);
        User scoutUser = context.scoutUser();
        String orgName = context.organizationName();
        ClubOrganization club = context.club();

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        BulkImportSourceType sourceType = filename.endsWith(".csv") ? BulkImportSourceType.FILE_CSV : BulkImportSourceType.FILE_EXCEL;

        List<BulkImportRowDto> rows;
        try {
            if (sourceType == BulkImportSourceType.FILE_CSV) {
                rows = parser.parseCsv(file.getInputStream());
            } else {
                rows = parser.parseExcel(file.getInputStream());
            }
        } catch (Exception e) {
            log.error("Error al procesar el archivo de importación: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error al leer el archivo: " + e.getMessage());
        }

        BulkImportJob job = BulkImportJob.builder()
                .scout(scoutUser)
                .club(club)
                .organizationName(orgName)
                .sourceType(sourceType)
                .fileName(file.getOriginalFilename())
                .status(BulkImportStatus.PENDING)
                .totalRows(rows.size())
                .processedRows(0)
                .successfulRows(0)
                .failedRows(0)
                .build();

        job = jobRepository.save(job);

        // Disparar procesamiento asíncrono solo tras confirmar commit para evitar race conditions
        scheduleAsyncProcessing(job.getId(), rows);

        return mapToResponseDto(job);
    }

    @Transactional
    public BulkImportJobResponseDto startGoogleSheetJob(UUID scoutUserId, String sheetUrlOrId) {
        ScoutContext context = validateAndResolveScoutContext(scoutUserId);
        User scoutUser = context.scoutUser();
        String orgName = context.organizationName();
        ClubOrganization club = context.club();

        List<BulkImportRowDto> rows;
        String sheetId = googleSheetsService.extractSheetId(sheetUrlOrId);
        try {
            List<List<Object>> values = googleSheetsService.readSheetData(sheetId);
            rows = parser.parseMatrixData(values);
        } catch (Exception e) {
            log.error("Error al leer la hoja de Google Sheets: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error al leer Google Sheets: " + e.getMessage());
        }

        BulkImportJob job = BulkImportJob.builder()
                .scout(scoutUser)
                .club(club)
                .organizationName(orgName)
                .sourceType(BulkImportSourceType.GOOGLE_SHEET)
                .sheetId(sheetId)
                .status(BulkImportStatus.PENDING)
                .totalRows(rows.size())
                .processedRows(0)
                .successfulRows(0)
                .failedRows(0)
                .build();

        job = jobRepository.save(job);

        // Disparar procesamiento asíncrono solo tras confirmar commit para evitar race conditions
        scheduleAsyncProcessing(job.getId(), rows);

        return mapToResponseDto(job);
    }

    @Transactional
    public ScoutIndividualAthleteResponse registerIndividualAthlete(
            UUID scoutUserId,
            ScoutIndividualAthleteRequest request
    ) {
        ScoutContext context = validateAndResolveScoutContext(scoutUserId);

        String normalizedGender = (request.getGenero() != null && !request.getGenero().isBlank())
                ? AthleteFieldNormalizer.normalizeGender(request.getGenero())
                : "MALE";
        String normalizedPrimaryPosition = AthleteFieldNormalizer.normalizePosition(request.getPosicionPrincipal(), true);
        String normalizedSecondaryPosition = (request.getPosicionSecundaria() != null && !request.getPosicionSecundaria().isBlank())
                ? AthleteFieldNormalizer.normalizePosition(request.getPosicionSecundaria(), false)
                : null;
        String normalizedFoot = (request.getPieDominante() != null && !request.getPieDominante().isBlank())
                ? AthleteFieldNormalizer.normalizePreferredFoot(request.getPieDominante())
                : "DERECHO";
        String normalizedNationality = (request.getNacionalidad() != null && !request.getNacionalidad().isBlank())
                ? AthleteFieldNormalizer.normalizeNationality(request.getNacionalidad())
                : "Venezuela";

        BulkImportRowDto row = BulkImportRowDto.builder()
                .rowNumber(1)
                .primerNombre(request.getPrimerNombre().trim())
                .segundoNombre(request.getSegundoNombre() != null ? request.getSegundoNombre().trim() : null)
                .primerApellido(request.getPrimerApellido().trim())
                .segundoApellido(request.getSegundoApellido() != null ? request.getSegundoApellido().trim() : null)
                .cedulaIdentidadAtleta(request.getCedulaIdentidadAtleta())
                .fechaNacimiento(request.getFechaNacimiento())
                .genero(normalizedGender)
                .posicionPrincipal(normalizedPrimaryPosition)
                .posicionSecundaria(normalizedSecondaryPosition)
                .pieDominante(normalizedFoot)
                .nacionalidad(normalizedNationality)
                .alturaCm(request.getAlturaCm())
                .pesoKg(request.getPesoKg())
                .nombreCompletoTutor(request.getNombreCompletoTutor().trim())
                .cedulaIdentidadTutor(request.getCedulaIdentidadTutor())
                .correoTutor(request.getCorreoTutor().trim())
                .telefonoTutor(request.getTelefonoTutor())
                .build();

        return processor.registerIndividualAthlete(row, context.club(), context.organizationName());
    }

    private ScoutContext validateAndResolveScoutContext(UUID scoutUserId) {
        User scoutUser = userRepository.findById(scoutUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario scout no encontrado"));

        if (scoutUser.getRole() == com.talentus.scout.domain.enums.UserRole.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El rol Administrador no tiene permitido realizar operaciones de carga masiva de atletas."
            );
        }

        Optional<ScoutProfile> optProfile = scoutProfileRepository.findByUserId(scoutUserId);
        if (optProfile.isEmpty() || !optProfile.get().isBulkImportEnabled()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Tu perfil de scout no tiene habilitada la funcionalidad de carga masiva. Contacta al administrador."
            );
        }

        String orgName = optProfile.get().getOrganizationName() != null
                ? optProfile.get().getOrganizationName()
                : "Organización Deportiva";
        ClubOrganization club = clubOrganizationRepository.findByNameIgnoreCase(orgName).orElse(null);

        return new ScoutContext(scoutUser, club, orgName);
    }

    private record ScoutContext(User scoutUser, ClubOrganization club, String organizationName) {
    }

    private void scheduleAsyncProcessing(UUID jobId, List<BulkImportRowDto> rows) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processor.processImportJob(jobId, rows);
                }
            });
        } else {
            processor.processImportJob(jobId, rows);
        }
    }

    @Transactional(readOnly = true)
    public BulkImportJobResponseDto getJobStatus(UUID jobId, UUID scoutUserId) {
        BulkImportJob job = jobRepository.findByIdAndScoutId(jobId, scoutUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trabajo de importación no encontrado"));
        return mapToResponseDto(job);
    }

    @Transactional(readOnly = true)
    public List<BulkImportJobResponseDto> getScoutJobs(UUID scoutUserId) {
        return jobRepository.findByScoutIdOrderByCreatedAtDesc(scoutUserId).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public byte[] downloadTemplate(String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return generateCsvTemplate();
        } else {
            return generateExcelTemplate();
        }
    }

    public byte[] downloadErrorReport(UUID jobId, UUID scoutUserId) {
        BulkImportJob job = jobRepository.findByIdAndScoutId(jobId, scoutUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trabajo no encontrado"));

        List<BulkImportRowErrorDto> warnings = parseWarnings(job.getErrorReportJson());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("Fila", "Atleta", "Motivo", "Acción Realizada").build())) {

            for (BulkImportRowErrorDto w : warnings) {
                printer.printRecord(w.getRowNumber(), w.getAthleteName(), w.getReason(), w.getActionTaken());
            }
            printer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando reporte: " + e.getMessage());
        }
    }

    private byte[] generateCsvTemplate() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(BulkImportParser.EXPECTED_HEADERS).build())) {

            printer.printRecord(
                    "Juan", "Carlos", "Pérez", "Gómez", "V-31234567", "2010-05-14",
                    "Masculino", "Delantero Centro", "Extremo Izquierdo", "Derecho",
                    "Venezuela", "165.50", "58.00",
                    "Carlos Pérez", "V-14234567", "tutor.perez@ejemplo.com", "+584121234567"
            );
            printer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al crear plantilla CSV: " + e.getMessage());
        }
    }

    private byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Atletas y Tutores");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < BulkImportParser.EXPECTED_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(BulkImportParser.EXPECTED_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            Row exampleRow = sheet.createRow(1);
            String[] example = {
                    "Juan", "Carlos", "Pérez", "Gómez", "V-31234567", "2010-05-14",
                    "Masculino", "Delantero Centro", "Extremo Izquierdo", "Derecho",
                    "Venezuela", "165.50", "58.00",
                    "Carlos Pérez", "V-14234567", "tutor.perez@ejemplo.com", "+584121234567"
            };
            for (int i = 0; i < example.length; i++) {
                exampleRow.createCell(i).setCellValue(example[i]);
            }

            // Listas desplegables (Validación de Datos en Excel para minimizar errores de escritura)
            addPromptValidation(sheet, 5,
                    "Fecha de Nacimiento", "Formato AAAA-MM-DD o DD/MM/AAAA. Obligatorio fecha en el pasado (edad formativa entre 4 y 21 años).");
            addDropdownValidation(sheet, 6, AthleteFieldNormalizer.CANONICAL_GENDERS_DISPLAY,
                    "Género", "Selecciona Masculino o Femenino");
            addDropdownValidation(sheet, 7, AthleteFieldNormalizer.CANONICAL_POSITIONS,
                    "Posición Principal", "Selecciona una posición oficial de la lista");
            addDropdownValidation(sheet, 8, AthleteFieldNormalizer.CANONICAL_POSITIONS,
                    "Posición Secundaria", "Selecciona una posición opcional o deja en blanco");
            addDropdownValidation(sheet, 9, AthleteFieldNormalizer.CANONICAL_FEET_DISPLAY,
                    "Pie Dominante", "Selecciona Derecho, Izquierdo o Ambidiestro");
            addDropdownValidation(sheet, 10, AthleteFieldNormalizer.AUTHORIZED_COUNTRIES,
                    "Nacionalidad", "Selecciona un país de la lista o escribe el país");

            for (int i = 0; i < BulkImportParser.EXPECTED_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al crear plantilla Excel: " + e.getMessage());
        }
    }

    private void addPromptValidation(Sheet sheet, int colIndex, String title, String message) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createCustomConstraint("TRUE");
        CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, colIndex, colIndex);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowPromptBox(true);
        validation.createPromptBox(title, message);
        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }

    private void addDropdownValidation(Sheet sheet, int colIndex, List<String> options, String title, String message) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options.toArray(new String[0]));
        CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, colIndex, colIndex);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setSuppressDropDownArrow(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
        validation.setShowPromptBox(true);
        validation.createPromptBox(title, message);
        validation.setShowErrorBox(true);
        validation.createErrorBox(title, "Se recomienda seleccionar una opción válida de la lista desplegable.");
        sheet.addValidationData(validation);
    }

    private BulkImportJobResponseDto mapToResponseDto(BulkImportJob job) {
        List<BulkImportRowErrorDto> warnings = parseWarnings(job.getErrorReportJson());

        return BulkImportJobResponseDto.builder()
                .id(job.getId())
                .organizationName(job.getOrganizationName())
                .sourceType(job.getSourceType())
                .fileName(job.getFileName())
                .sheetId(job.getSheetId())
                .status(job.getStatus())
                .totalRows(job.getTotalRows())
                .processedRows(job.getProcessedRows())
                .successfulRows(job.getSuccessfulRows())
                .failedRows(job.getFailedRows())
                .errorSummary(job.getErrorSummary())
                .warnings(warnings)
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private List<BulkImportRowErrorDto> parseWarnings(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<BulkImportRowErrorDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
