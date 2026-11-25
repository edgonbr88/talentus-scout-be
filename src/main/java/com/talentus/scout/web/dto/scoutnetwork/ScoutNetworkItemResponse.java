package com.talentus.scout.web.dto.scoutnetwork;

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
public class ScoutNetworkItemResponse {
    private UUID scoutProfileId;
    private UUID userId;
    private String fullName;
    private String profilePhotoUrl;
    private String organizationName;
    private UUID clubId;
    private String clubLogoUrl;
    private boolean clubVerifiedOfficial;
    private String scoutRole;
    private String country;
    private String state;
    private String linkedinUrl;
    private boolean sameClubAsViewer;
    private boolean whatsappAvailable;
    private boolean emailAvailable;
    private Instant createdAt;
}
