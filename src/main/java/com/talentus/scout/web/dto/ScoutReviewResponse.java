package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
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
public class ScoutReviewResponse {

    private UUID scoutProfileId;
    private UUID userId;
    private String fullName;
    private ScoutVerificationStatus verificationStatus;
    private AccountStatus accountStatus;
    private UUID reviewedByAdminId;
    private String reviewedByAdminName;
    private Instant reviewedAt;
    private String rejectionReason;
    private String message;
}
