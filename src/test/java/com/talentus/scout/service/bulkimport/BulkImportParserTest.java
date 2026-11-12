package com.talentus.scout.service.bulkimport;

import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkImportParserTest {

    private BulkImportParser parser;

    @BeforeEach
    void setUp() {
        parser = new BulkImportParser();
    }

    @Test
    void testParseCsvSuccess() throws Exception {
        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Carlos,,Santana,Pérez,V-30111222,2011-04-12,MALE,Mediocentro,Mediapunta,LEFT,Venezolana,160.00,52.50,Roberto Santana,V-12345678,roberto@ejemplo.com,+584141112233\n" +
                "Gabriel,Alejandro,Mendoza,,V-31999888,15/08/2012,Masculino,Portero,,Derecho,,172.00,60.00,Maria Mendoza,V-15888999,maria@ejemplo.com,+584129998877\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(rows);
        assertEquals(2, rows.size());

        BulkImportRowDto first = rows.get(0);
        assertEquals("Carlos", first.getPrimerNombre());
        assertEquals("Santana", first.getPrimerApellido());
        assertEquals("V-30111222", first.getCedulaIdentidadAtleta());
        assertEquals("2011-04-12", first.getFechaNacimiento().toString());
        assertEquals("MALE", first.getGenero());
        assertEquals("LEFT", first.getPieDominante());
        assertEquals("roberto@ejemplo.com", first.getCorreoTutor());

        BulkImportRowDto second = rows.get(1);
        assertEquals("Gabriel", second.getPrimerNombre());
        assertEquals("Alejandro", second.getSegundoNombre());
        assertEquals("2012-08-15", second.getFechaNacimiento().toString());
        assertEquals("MALE", second.getGenero());
        assertEquals("RIGHT", second.getPieDominante());
        assertEquals("Venezolana", second.getNacionalidad());
    }

    @Test
    void testMissingRequiredHeadersThrowsException() {
        String invalidCsv = "nombre,apellido,correo\nJuan,Perez,juan@mail.com\n";
        assertThrows(IllegalArgumentException.class, () ->
                parser.parseCsv(new ByteArrayInputStream(invalidCsv.getBytes(StandardCharsets.UTF_8)))
        );
    }
}
