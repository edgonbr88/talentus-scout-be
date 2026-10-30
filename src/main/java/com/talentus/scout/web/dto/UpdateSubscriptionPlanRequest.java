package com.talentus.scout.web.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSubscriptionPlanRequest {

    @NotBlank(message = "El nombre del plan es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String name;

    @NotNull(message = "El precio en USD es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal priceUsd;

    @NotNull(message = "La duración en días es obligatoria")
    @Min(value = 1, message = "La duración mínima debe ser de al menos 1 día")
    private Integer durationDays;

    @NotNull(message = "El límite de videos es obligatorio")
    @Min(value = 0, message = "El límite de videos no puede ser negativo")
    private Integer maxVideos;

    @NotNull(message = "El flag de acceso a radar es obligatorio")
    private Boolean hasRadarViews;

    @NotNull(message = "El flag de generación de CV en PDF es obligatorio")
    private Boolean hasPdfCv;

    @NotNull(message = "El estado de activación es obligatorio")
    private Boolean isActive;
}
