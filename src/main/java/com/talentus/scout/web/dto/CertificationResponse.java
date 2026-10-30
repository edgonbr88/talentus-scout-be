package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.BadgeType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificationResponse {

    private UUID id;
    private UUID athleteId;
    private String athleteFullName;
    private BadgeType badgeType;
    private String requestMethod;
    private String status;
    private String title;
    private String description;
    private String evidenceDocumentUrl;
    private String targetEmail;
    private String targetName;
    private String targetOrganization;
    private String tokenHash;
    private String certificationLink;
    private Instant tokenExpiresAt;
    private String certifierFullName;
    private String certifierLicenseId;
    private String certifierNotes;
    private String certificationDataJson;
    private Instant completedAt;
    private Instant createdAt;
    private UUID badgeId;
}
