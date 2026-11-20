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
        assertNull(first.getValidationError());
        assertEquals("Carlos", first.getPrimerNombre());
        assertEquals("Santana", first.getPrimerApellido());
        assertEquals("V-30111222", first.getCedulaIdentidadAtleta());
        assertEquals("2011-04-12", first.getFechaNacimiento().toString());
        assertEquals("MALE", first.getGenero());
        assertEquals("Mediocampista Central", first.getPosicionPrincipal());
        assertEquals("Mediocampista Ofensivo", first.getPosicionSecundaria());
        assertEquals("IZQUIERDO", first.getPieDominante());
        assertEquals("Venezuela", first.getNacionalidad());
        assertEquals("roberto@ejemplo.com", first.getCorreoTutor());

        BulkImportRowDto second = rows.get(1);
        assertNull(second.getValidationError());
        assertEquals("Gabriel", second.getPrimerNombre());
        assertEquals("Alejandro", second.getSegundoNombre());
        assertEquals("2012-08-15", second.getFechaNacimiento().toString());
        assertEquals("MALE", second.getGenero());
        assertEquals("Portero", second.getPosicionPrincipal());
        assertNull(second.getPosicionSecundaria());
        assertEquals("DERECHO", second.getPieDominante());
        assertEquals("Venezuela", second.getNacionalidad());
    }

    @Test
    void testCaseInsensitiveAccentInsensitiveAndSynonymNormalization() throws Exception {
        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Lucia,,Rojas,,V-32111222,2011-01-10,femenino,DELANTERO_CENTRO,ninguna,derecho,perú,162,54,Ana Rojas,V-11222333,ana@mail.com,+584121112233\n" +
                "Mateo,,Diaz,,V-32111223,2010-03-20,hombre,arquero,pivote defensivo,zurdo,chilena,175,65,Luis Diaz,V-11222334,luis@mail.com,+584121112234\n" +
                "Diego,,Silva,,V-32111224,2009-07-15,M,mediocampista ofensivo,extremo derecho,ambidiestro,colombiano,170,62,Rosa Silva,V-11222335,rosa@mail.com,+584121112235\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, rows.size());

        assertNull(rows.get(0).getValidationError());
        assertEquals("FEMALE", rows.get(0).getGenero());
        assertEquals("Delantero Centro", rows.get(0).getPosicionPrincipal());
        assertNull(rows.get(0).getPosicionSecundaria());
        assertEquals("DERECHO", rows.get(0).getPieDominante());
        assertEquals("Perú", rows.get(0).getNacionalidad());

        assertNull(rows.get(1).getValidationError());
        assertEquals("MALE", rows.get(1).getGenero());
        assertEquals("Portero", rows.get(1).getPosicionPrincipal());
        assertEquals("Mediocampista Defensivo", rows.get(1).getPosicionSecundaria());
        assertEquals("IZQUIERDO", rows.get(1).getPieDominante());
        assertEquals("Chile", rows.get(1).getNacionalidad());

        assertNull(rows.get(2).getValidationError());
        assertEquals("MALE", rows.get(2).getGenero());
        assertEquals("Mediocampista Ofensivo", rows.get(2).getPosicionPrincipal());
        assertEquals("Extremo Derecho", rows.get(2).getPosicionSecundaria());
        assertEquals("AMBIDIESTRO", rows.get(2).getPieDominante());
        assertEquals("Colombia", rows.get(2).getNacionalidad());
    }

    @Test
    void testInvalidCategoryProducesRowValidationErrorInsteadOfThrowing() throws Exception {
        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Pedro,,Lopez,,V-33111222,2011-04-12,MALE,PosicionInventada,,Cabeza,Venezolana,160,55,Carlos Lopez,V-12345678,carlos@mail.com,+584121112233\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).getValidationError());
        assertTrue(rows.get(0).getValidationError().contains("Posición inválida"));
        assertTrue(rows.get(0).getValidationError().contains("Pie dominante inválido"));
    }

    @Test
    void testMissingRequiredHeadersThrowsException() {
        String invalidCsv = "nombre,apellido,correo\nJuan,Perez,juan@mail.com\n";
        assertThrows(IllegalArgumentException.class, () ->
                parser.parseCsv(new ByteArrayInputStream(invalidCsv.getBytes(StandardCharsets.UTF_8)))
        );
    }

    @Test
    void testFutureBirthDateProducesRowValidationError() throws Exception {
        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Futuro,,Atleta,,V-34111222,2035-05-14,MALE,Delantero Centro,,Derecho,Venezuela,160,55,Tutor Legal,V-12345678,tutor@mail.com,+584121112233\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).getValidationError());
        assertTrue(rows.get(0).getValidationError().contains("La fecha de nacimiento no puede ser futura"));
    }

    @Test
    void testTodayBirthDateProducesRowValidationError() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Recien,,Nacido,,V-34111223," + today + ",MALE,Delantero Centro,,Derecho,Venezuela,160,55,Tutor Legal,V-12345678,tutor@mail.com,+584121112233\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).getValidationError());
        assertTrue(rows.get(0).getValidationError().contains("La fecha de nacimiento no puede ser futura ni igual a la fecha actual"));
    }

    @Test
    void testAgeOutOfFormativeRangeProducesRowValidationError() throws Exception {
        java.time.LocalDate tooYoung = java.time.LocalDate.now().minusYears(2);
        java.time.LocalDate tooOld = java.time.LocalDate.now().minusYears(23);

        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Bebe,,Jugador,,V-34111224," + tooYoung + ",MALE,Delantero Centro,,Derecho,Venezuela,160,55,Tutor Legal,V-12345678,tutor@mail.com,+584121112233\n" +
                "Veterano,,Jugador,,V-34111225," + tooOld + ",MALE,Delantero Centro,,Derecho,Venezuela,160,55,Tutor Legal,V-12345678,tutor@mail.com,+584121112233\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, rows.size());
        assertNotNull(rows.get(0).getValidationError());
        assertTrue(rows.get(0).getValidationError().contains("al menos 4 años de edad"));

        assertNotNull(rows.get(1).getValidationError());
        assertTrue(rows.get(1).getValidationError().contains("categorías formativas de hasta 21 años"));
    }

    @Test
    void testValidYouthAthlete18To21ParsedSuccessfully() throws Exception {
        java.time.LocalDate youthAge19 = java.time.LocalDate.now().minusYears(19).minusDays(10);
        String csvContent = "primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,cedula_identidad_atleta,fecha_nacimiento,genero,posicion_principal,posicion_secundaria,pie_dominante,nacionalidad,altura_cm,peso_kg,nombre_completo_tutor,cedula_identidad_tutor,correo_tutor,telefono_tutor\n" +
                "Juvenil,,Mayor,,V-34111226," + youthAge19 + ",MALE,Delantero Centro,,Derecho,Venezuela,178,72,Tutor Legal,V-12345678,tutor@mail.com,+584121112233\n";

        List<BulkImportRowDto> rows = parser.parseCsv(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getValidationError());
        assertEquals("Juvenil", rows.get(0).getPrimerNombre());
    }
}
