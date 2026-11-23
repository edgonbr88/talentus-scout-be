package com.talentus.scout.web.dto.bulkimport;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutIndividualAthleteRequest {

    @NotBlank(message = "El primer nombre del atleta es obligatorio")
    private String primerNombre;

    private String segundoNombre;

    @NotBlank(message = "El primer apellido del atleta es obligatorio")
    private String primerApellido;

    private String segundoApellido;

    private String cedulaIdentidadAtleta;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate fechaNacimiento;

    private String genero;

    @NotBlank(message = "La posición principal es obligatoria")
    private String posicionPrincipal;

    private String posicionSecundaria;

    private String pieDominante;

    private String nacionalidad;

    @DecimalMin(value = "90.0", message = "La estatura mínima permitida es 90 cm")
    @DecimalMax(value = "230.0", message = "La estatura máxima permitida es 230 cm")
    private BigDecimal alturaCm;

    @DecimalMin(value = "20.0", message = "El peso mínimo permitido es 20 kg")
    @DecimalMax(value = "150.0", message = "El peso máximo permitido es 150 kg")
    private BigDecimal pesoKg;

    @NotBlank(message = "El nombre completo del tutor es obligatorio")
    private String nombreCompletoTutor;

    private String cedulaIdentidadTutor;

    @NotBlank(message = "El correo electrónico del tutor es obligatorio")
    @Email(message = "El correo electrónico del tutor no tiene un formato válido")
    private String correoTutor;

    @Pattern(
            regexp = "^$|\\+[1-9]\\d{7,14}$",
            message = "El teléfono del tutor debe estar en formato internacional E.164 (ej. +584121234567)"
    )
    private String telefonoTutor;
}
