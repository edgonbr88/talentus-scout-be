package com.talentus.scout.service.bulkimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.repository.BulkImportJobRepository;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BulkImportServiceTemplateTest {

    private BulkImportParser parser;
    private BulkImportService service;

    @BeforeEach
    void setUp() {
        parser = new BulkImportParser();
        service = new BulkImportService(
                mock(BulkImportJobRepository.class),
                mock(UserRepository.class),
                mock(ScoutProfileRepository.class),
                mock(ClubOrganizationRepository.class),
                parser,
                mock(GoogleSheetsService.class),
                mock(BulkImportProcessor.class),
                new ObjectMapper()
        );
    }

    @Test
    void testExcelTemplateContainsDropdownValidationsAndRoundTripsThroughParser() throws Exception {
        byte[] xlsxBytes = service.downloadTemplate("xlsx");
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            List<? extends DataValidation> validations = sheet.getDataValidations();
            assertEquals(5, validations.size(), "Debe incluir 5 listas desplegables (género, pos principal, pos secundaria, pie, nacionalidad)");
        }

        List<BulkImportRowDto> parsed = parser.parseExcel(new ByteArrayInputStream(xlsxBytes));
        assertEquals(1, parsed.size());
        BulkImportRowDto example = parsed.get(0);
        assertNull(example.getValidationError());
        assertEquals("MALE", example.getGenero());
        assertEquals("Delantero Centro", example.getPosicionPrincipal());
        assertEquals("Extremo Izquierdo", example.getPosicionSecundaria());
        assertEquals("DERECHO", example.getPieDominante());
        assertEquals("Venezuela", example.getNacionalidad());
    }

    @Test
    void testCsvTemplateRoundTripsThroughParser() throws Exception {
        byte[] csvBytes = service.downloadTemplate("csv");
        List<BulkImportRowDto> parsed = parser.parseCsv(new ByteArrayInputStream(csvBytes));
        assertEquals(1, parsed.size());
        BulkImportRowDto example = parsed.get(0);
        assertNull(example.getValidationError());
        assertEquals("MALE", example.getGenero());
        assertEquals("Delantero Centro", example.getPosicionPrincipal());
        assertEquals("Extremo Izquierdo", example.getPosicionSecundaria());
        assertEquals("DERECHO", example.getPieDominante());
        assertEquals("Venezuela", example.getNacionalidad());
    }
}
