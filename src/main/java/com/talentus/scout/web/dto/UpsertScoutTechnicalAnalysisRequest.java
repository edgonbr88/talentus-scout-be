package com.talentus.scout.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertScoutTechnicalAnalysisRequest {

    @NotEmpty(message = "Debes incluir al menos una fortaleza o virtud destacada")
    @Size(min = 1, max = 10, message = "Puedes registrar entre 1 y 10 fortalezas destacadas")
    private List<@Valid TechnicalAnalysisItemDto> strengths;

    @NotEmpty(message = "Debes incluir al menos un área de mejora o enfoque formativo")
    @Size(min = 1, max = 10, message = "Puedes registrar entre 1 y 10 áreas de mejora")
    private List<@Valid TechnicalAnalysisItemDto> improvementAreas;

    @NotBlank(message = "El dictamen de scouting es obligatorio")
    @Size(max = 2000, message = "El dictamen de scouting no puede superar los 2000 caracteres")
    private String scoutingVerdict;
}
