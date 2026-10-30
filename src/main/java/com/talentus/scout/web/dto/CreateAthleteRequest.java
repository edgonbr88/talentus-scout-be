package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAthleteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede exceder 100 caracteres")
    private String lastName;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser en el pasado")
    private LocalDate birthDate;

    @Builder.Default
    private String gender = "MALE";

    @NotBlank(message = "La posición principal es obligatoria")
    @Size(max = 50)
    private String primaryPosition;

    @Size(max = 50)
    private String secondaryPosition;

    @NotBlank(message = "El pie hábil es obligatorio")
    @Size(max = 20)
    private String preferredFoot;

    @Pattern(regexp = "^(Venezuela|Colombia|Chile|Perú|Peru|Ecuador|Venezolana|Colombiana|Chilena|Peruana|Ecuatoriana)$", message = "La nacionalidad debe corresponder a un país autorizado (Venezuela, Colombia, Chile, Perú, Ecuador)")
    @Builder.Default
    private String nationality = "Venezuela";

    @Size(max = 150)
    private String secondaryPassports;

    private UUID currentClubId;

    @Size(max = 150, message = "El nombre del club personalizado no puede exceder 150 caracteres")
    private String customClubName;

    @Size(max = 100, message = "La ciudad del club personalizado no puede exceder 100 caracteres")
    private String customClubCity;

    @Size(max = 100, message = "El estado del club personalizado no puede exceder 100 caracteres")
    private String customClubState;

    @Size(max = 100, message = "El país del club personalizado no puede exceder 100 caracteres")
    private String customClubCountry;

    @Size(max = 50)
    private String federationLicenseNumber;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String profilePhotoUrl;

    private String bio;

    @Builder.Default
    private Boolean isPublic = true;

    public boolean isPublic() {
        return isPublic != null ? isPublic : true;
    }
}
