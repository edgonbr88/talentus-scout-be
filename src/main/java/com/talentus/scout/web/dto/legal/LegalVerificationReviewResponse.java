package com.talentus.scout.web.dto.legal;

import com.talentus.scout.domain.enums.LegalVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalVerificationReviewResponse {

    private UUID athleteId;
    private LegalVerificationStatus decision;
    private String notes;
    private String verifiedByEmail;
    private Instant verifiedAt;
    private String message;
}
