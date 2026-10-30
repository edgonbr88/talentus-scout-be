package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.BadgeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTokenCertificationRequest {

    @NotNull(message = "El ID del atleta es obligatorio")
    private UUID athleteId;

    @NotNull(message = "El tipo de certificación es obligatorio")
    private BadgeType badgeType;

    @NotBlank(message = "El título o aval es obligatorio")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    private String title;

    private String description;

    @NotBlank(message = "El correo del certificador es obligatorio")
    @Email(message = "Debe proporcionar una dirección de correo válida")
    @Size(max = 150)
    private String targetEmail;

    @NotBlank(message = "El nombre del certificador o profesional es obligatorio")
    @Size(max = 150)
    private String targetName;

    @Size(max = 150)
    private String targetOrganization;
}
