package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVexPaySessionRequest {

    @NotNull(message = "El atleta es obligatorio")
    private UUID athleteId;

    @NotBlank(message = "El plan de suscripción es obligatorio")
    private String planId;

    private String discountCode;
}
