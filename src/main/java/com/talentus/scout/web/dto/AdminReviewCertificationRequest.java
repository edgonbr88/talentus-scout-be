package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminReviewCertificationRequest {

    @NotBlank(message = "La decisión de revisión es obligatoria")
    @Pattern(regexp = "APPROVE|REJECT", message = "La decisión debe ser APPROVE o REJECT")
    private String decision;

    private String adminNotes;
}
