package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitTokenCertificationRequest {

    @NotBlank(message = "El nombre completo del profesional o certificador es obligatorio")
    @Size(max = 150)
    private String certifierFullName;

    @NotBlank(message = "La matrícula profesional, credencial o cargo oficial es obligatorio")
    @Size(max = 100)
    private String certifierLicenseId;

    private String certifierNotes;

    // Campos médicos / antropométricos opcionales
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bodyFatPercent;
    private BigDecimal muscleMassKg;

    // Campos deportivos / logros opcionales
    private Integer goalsScored;
    private String tournamentName;
    private String achievementTitle;
}
