package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAthleteAchievementRequest {

    @NotBlank(message = "El título del logro es obligatorio")
    @Size(max = 150, message = "El título del logro no puede superar los 150 caracteres")
    private String title;

    @NotBlank(message = "La categoría del logro es obligatoria")
    @Pattern(
            regexp = "^(TORNEO|INDIVIDUAL|CONVOCATORIA|OTRO)$",
            message = "Categoría inválida. Valores permitidos: TORNEO, INDIVIDUAL, CONVOCATORIA, OTRO"
    )
    private String category;

    @PastOrPresent(message = "La fecha del logro no puede ser futura")
    private LocalDate achievementDate;

    @Size(max = 500, message = "La descripción del logro no puede superar los 500 caracteres")
    private String description;
}
