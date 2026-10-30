package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotNull;
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
public class ReviewScoutRequest {

    @NotNull(message = "La decisión de auditoría es obligatoria (APPROVE o REJECT)")
    private ReviewScoutDecision decision;

    private String rejectionReason;
}
