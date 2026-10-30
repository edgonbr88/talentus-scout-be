package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateCouponRequest {

    @NotBlank(message = "El código promocional es obligatorio")
    private String code;

    @NotNull(message = "El ID del atleta es obligatorio")
    private UUID athleteId;

    @NotBlank(message = "El ID del plan es obligatorio")
    private String planId;
}
