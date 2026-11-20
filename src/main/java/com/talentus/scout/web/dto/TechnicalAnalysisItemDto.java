package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalAnalysisItemDto {

    @NotBlank(message = "El título del criterio técnico es obligatorio")
    @Size(max = 120, message = "El título no puede superar los 120 caracteres")
    private String title;

    @NotBlank(message = "La descripción del criterio técnico es obligatoria")
    @Size(max = 600, message = "La descripción no puede superar los 600 caracteres")
    private String description;
}
