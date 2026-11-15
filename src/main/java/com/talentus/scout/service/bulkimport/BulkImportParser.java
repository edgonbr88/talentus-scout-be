package com.talentus.scout.service.bulkimport;

import com.talentus.scout.util.AthleteFieldNormalizer;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class BulkImportParser {

    public static final String[] EXPECTED_HEADERS = {
            "primer_nombre", "segundo_nombre", "primer_apellido", "segundo_apellido",
            "cedula_identidad_atleta", "fecha_nacimiento", "genero", "posicion_principal",
            "posicion_secundaria", "pie_dominante", "nacionalidad", "altura_cm",
            "peso_kg", "nombre_completo_tutor", "cedula_identidad_tutor",
            "correo_tutor", "telefono_tutor"
    };

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LATIN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<BulkImportRowDto> parseCsv(InputStream inputStream) throws Exception {
        List<BulkImportRowDto> rows = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            validateHeaders(parser.getHeaderNames());

            int rowIdx = 1; // 1-indexed for user readability
            for (CSVRecord record : parser) {
                rowIdx++;
                BulkImportRowDto dto = mapCsvRecordToDto(record, rowIdx);
                if (dto != null) {
                    rows.add(dto);
                }
            }
        }
        return rows;
    }

    public List<BulkImportRowDto> parseExcel(InputStream inputStream) throws Exception {
        List<BulkImportRowDto> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("La hoja de cálculo está vacía");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("No se encontró la fila de encabezados en la hoja de cálculo");
            }

            Map<String, Integer> headerMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String val = getCellStringValue(cell).trim().toLowerCase();
                if (!val.isEmpty()) {
                    headerMap.put(val, cell.getColumnIndex());
                }
            }

            validateHeaders(new ArrayList<>(headerMap.keySet()));

            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                BulkImportRowDto dto = mapExcelRowToDto(row, headerMap, r + 1);
                if (dto != null) {
                    rows.add(dto);
                }
            }
        }
        return rows;
    }

    public List<BulkImportRowDto> parseMatrixData(List<List<Object>> values) {
        List<BulkImportRowDto> rows = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron datos en la hoja seleccionada");
        }

        List<Object> headerList = values.get(0);
        Map<String, Integer> headerMap = new HashMap<>();
        List<String> headerNames = new ArrayList<>();
        for (int i = 0; i < headerList.size(); i++) {
            String col = String.valueOf(headerList.get(i)).trim().toLowerCase();
            if (!col.isEmpty()) {
                headerMap.put(col, i);
                headerNames.add(col);
            }
        }

        validateHeaders(headerNames);

        for (int r = 1; r < values.size(); r++) {
            List<Object> row = values.get(r);
            if (row == null || row.isEmpty()) {
                continue;
            }
            BulkImportRowDto dto = mapMatrixRowToDto(row, headerMap, r + 1);
            if (dto != null) {
                rows.add(dto);
            }
        }
        return rows;
    }

    private void validateHeaders(List<String> foundHeaders) {
        Set<String> normalizedFound = new HashSet<>();
        for (String h : foundHeaders) {
            normalizedFound.add(h.trim().toLowerCase());
        }

        List<String> missing = new ArrayList<>();
        for (String expected : EXPECTED_HEADERS) {
            if (!normalizedFound.contains(expected)) {
                missing.add(expected);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("La plantilla no contiene todas las columnas requeridas. Faltan: " + String.join(", ", missing));
        }
    }

    private BulkImportRowDto mapCsvRecordToDto(CSVRecord record, int rowNum) {
        return buildRowDto(rowNum, col -> record.isMapped(col) ? record.get(col) : null);
    }

    private BulkImportRowDto mapExcelRowToDto(Row row, Map<String, Integer> headerMap, int rowNum) {
        return buildRowDto(rowNum, col -> getVal(row, headerMap, col));
    }

    private BulkImportRowDto mapMatrixRowToDto(List<Object> row, Map<String, Integer> headerMap, int rowNum) {
        return buildRowDto(rowNum, col -> getMatrixVal(row, headerMap, col));
    }

    private BulkImportRowDto buildRowDto(int rowNum, java.util.function.Function<String, String> fieldExtractor) {
        List<String> rowErrors = new ArrayList<>();

        String rawBirthDate = clean(fieldExtractor.apply("fecha_nacimiento"));
        LocalDate parsedBirthDate = parseDate(rawBirthDate);
        if (rawBirthDate != null && parsedBirthDate == null) {
            rowErrors.add("Formato de fecha inválido en 'fecha_nacimiento': '" + rawBirthDate + "' (use AAAA-MM-DD o DD/MM/AAAA)");
        }

        String genero = normalizeSafely(() -> AthleteFieldNormalizer.normalizeGender(clean(fieldExtractor.apply("genero"))), "MALE", rowErrors);
        String posicionPrincipal = normalizeSafely(() -> AthleteFieldNormalizer.normalizePosition(clean(fieldExtractor.apply("posicion_principal")), true), null, rowErrors);
        String posicionSecundaria = normalizeSafely(() -> AthleteFieldNormalizer.normalizePosition(clean(fieldExtractor.apply("posicion_secundaria")), false), null, rowErrors);
        String pieDominante = normalizeSafely(() -> AthleteFieldNormalizer.normalizePreferredFoot(clean(fieldExtractor.apply("pie_dominante"))), "DERECHO", rowErrors);
        String nacionalidad = normalizeSafely(() -> AthleteFieldNormalizer.normalizeNationality(clean(fieldExtractor.apply("nacionalidad"))), "Venezuela", rowErrors);

        return BulkImportRowDto.builder()
                .rowNumber(rowNum)
                .primerNombre(clean(fieldExtractor.apply("primer_nombre")))
                .segundoNombre(clean(fieldExtractor.apply("segundo_nombre")))
                .primerApellido(clean(fieldExtractor.apply("primer_apellido")))
                .segundoApellido(clean(fieldExtractor.apply("segundo_apellido")))
                .cedulaIdentidadAtleta(clean(fieldExtractor.apply("cedula_identidad_atleta")))
                .fechaNacimiento(parsedBirthDate)
                .genero(genero)
                .posicionPrincipal(posicionPrincipal)
                .posicionSecundaria(posicionSecundaria)
                .pieDominante(pieDominante)
                .nacionalidad(nacionalidad)
                .alturaCm(parseBigDecimal(clean(fieldExtractor.apply("altura_cm"))))
                .pesoKg(parseBigDecimal(clean(fieldExtractor.apply("peso_kg"))))
                .nombreCompletoTutor(clean(fieldExtractor.apply("nombre_completo_tutor")))
                .cedulaIdentidadTutor(clean(fieldExtractor.apply("cedula_identidad_tutor")))
                .correoTutor(clean(fieldExtractor.apply("correo_tutor")))
                .telefonoTutor(clean(fieldExtractor.apply("telefono_tutor")))
                .validationError(rowErrors.isEmpty() ? null : String.join("; ", rowErrors))
                .build();
    }

    private String normalizeSafely(java.util.function.Supplier<String> normalizer, String fallback, List<String> errors) {
        try {
            return normalizer.get();
        } catch (IllegalArgumentException ex) {
            errors.add(ex.getMessage());
            return fallback;
        }
    }

    private String getVal(Row row, Map<String, Integer> headerMap, String colName) {
        Integer idx = headerMap.get(colName.toLowerCase());
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        return getCellStringValue(cell);
    }

    private String getMatrixVal(List<Object> row, Map<String, Integer> headerMap, String colName) {
        Integer idx = headerMap.get(colName.toLowerCase());
        if (idx == null || idx >= row.size()) return null;
        Object val = row.get(idx);
        return val != null ? String.valueOf(val).trim() : null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date d = cell.getDateCellValue();
                    yield d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(ISO_DATE);
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num)) {
                        yield String.valueOf((long) num);
                    } else {
                        yield String.valueOf(num);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellStringValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        val = val.trim();
        try {
            return LocalDate.parse(val, ISO_DATE);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(val, LATIN_DATE);
            } catch (Exception e) {
                log.warn("Formato de fecha inválido para: {}", val);
                return null;
            }
        }
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val.trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}
