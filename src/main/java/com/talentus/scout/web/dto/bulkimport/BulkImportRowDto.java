package com.talentus.scout.web.dto.bulkimport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRowDto {
    private int rowNumber;
    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private String cedulaIdentidadAtleta;
    private LocalDate fechaNacimiento;
    private String genero;
    private String posicionPrincipal;
    private String posicionSecundaria;
    private String pieDominante;
    private String nacionalidad;
    private BigDecimal alturaCm;
    private BigDecimal pesoKg;
    private String nombreCompletoTutor;
    private String cedulaIdentidadTutor;
    private String correoTutor;
    private String telefonoTutor;
    private String validationError;
}
