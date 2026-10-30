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
public class PublicTokenCertificationResponse {

    private String token;
    private UUID athleteId;
    private String athleteFullName;
    private String athleteCategory;
    private String athleteClubName;
    private String athletePosition;
    private BadgeType badgeType;
    private String badgeTypeLabel;
    private String title;
    private String description;
    private String targetEmail;
    private String targetName;
    private String targetOrganization;
    private Instant expiresAt;
    private boolean expired;
    private boolean alreadyCompleted;
}
