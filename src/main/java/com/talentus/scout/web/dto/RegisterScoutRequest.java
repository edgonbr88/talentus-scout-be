package com.talentus.scout.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterScoutRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String fullName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "Formato de correo electrónico inválido")
    @Size(max = 255, message = "El correo no puede exceder 255 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "El número telefónico es obligatorio")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "El número telefónico debe estar en formato internacional E.164 (ej: +584121234567)")
    private String phoneNumber;

    @Size(max = 150, message = "El nombre de la organización no puede exceder 150 caracteres")
    private String organizationName;

    private java.util.UUID organizationClubId;

    @Size(max = 100, message = "La ciudad de la organización no puede exceder 100 caracteres")
    private String customClubCity;

    @Size(max = 100, message = "El estado de la organización no puede exceder 100 caracteres")
    private String customClubState;

    @Size(max = 100, message = "El país de la organización no puede exceder 100 caracteres")
    private String customClubCountry;

    @jakarta.validation.constraints.AssertTrue(message = "El nombre de la organización o selección de club es obligatorio")
    public boolean isOrganizationProvided() {
        return (organizationClubId != null) || (organizationName != null && !organizationName.trim().isEmpty());
    }

    @NotBlank(message = "El rol o cargo de scout es obligatorio (ej. Reclutador Oficial, Agente, Entrenador)")
    @Size(max = 100, message = "El rol no puede exceder 100 caracteres")
    private String scoutRole;

    @Size(max = 255, message = "El enlace de LinkedIn no puede exceder 255 caracteres")
    private String linkedinUrl;

    @NotNull(message = "El archivo de credenciales o acreditación es obligatorio")
    private MultipartFile credentialsFile;
}
