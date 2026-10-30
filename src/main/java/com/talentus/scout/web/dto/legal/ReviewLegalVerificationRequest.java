package com.talentus.scout.web.dto.legal;

import com.talentus.scout.domain.enums.LegalVerificationStatus;
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
public class ReviewLegalVerificationRequest {

    @NotNull(message = "La decisión de auditoría es obligatoria (APPROVED o REJECTED)")
    private LegalVerificationStatus decision;

    private String notes;
}
