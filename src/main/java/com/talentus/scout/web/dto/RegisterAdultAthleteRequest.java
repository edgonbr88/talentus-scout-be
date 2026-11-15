package com.talentus.scout.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterAdultAthleteRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String fullName;

    private String firstName;
    private String lastName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "Formato de correo electrónico inválido")
    @Size(max = 255, message = "El correo no puede exceder 255 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "El número telefónico es obligatorio")
    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    private String phoneNumber;

    @Builder.Default
    private String country = "Venezuela";

    @Builder.Default
    private String state = "Distrito Capital";

    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String address;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;

    @NotNull(message = "Debes aceptar los términos y condiciones")
    @AssertTrue(message = "Debes aceptar los términos y condiciones de la plataforma")
    private Boolean acceptTerms;

    @NotNull(message = "Debes autorizar la cláusula de difusión institucional y cesión de imagen")
    @AssertTrue(message = "Debes autorizar la cláusula de difusión institucional y cesión de imagen")
    private Boolean acceptInstitutionalDiffusion;

    // Ficha Deportiva Inicial
    @NotBlank(message = "La posición principal es obligatoria")
    private String primaryPosition;

    private String secondaryPosition;

    @NotBlank(message = "El pie hábil es obligatorio")
    private String preferredFoot;

    @Builder.Default
    private String gender = "MALE";

    @Builder.Default
    private String nationality = "Venezolana";

    private String currentClubName;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String bio;

    // Verificación de Identidad (Opcional en registro inicial)
    private String idDocumentType;

    private String idDocumentNumber;

    private MultipartFile idDocumentFile;

    private MultipartFile selfieFile;
}
