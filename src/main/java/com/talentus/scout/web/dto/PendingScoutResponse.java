package com.talentus.scout.web.dto;

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
public class PendingScoutResponse {

    private UUID scoutProfileId;
    private UUID userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String organizationName;
    private String scoutRole;
    private String credentialsDocumentUrl;
    private String linkedinUrl;
    private ScoutVerificationStatus verificationStatus;
    private Instant createdAt;
}
