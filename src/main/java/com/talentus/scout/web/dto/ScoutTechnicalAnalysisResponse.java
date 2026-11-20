package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutTechnicalAnalysisResponse {
    private UUID id;
    private UUID athleteId;
    private UUID scoutUserId;
    private String scoutFullName;
    private String scoutRole;
    private String organizationName;
    private List<TechnicalAnalysisItemDto> strengths;
    private List<TechnicalAnalysisItemDto> improvementAreas;
    private String scoutingVerdict;
    private boolean editableByCurrentUser;
    private boolean deletableByCurrentUser;
    private Instant createdAt;
    private Instant updatedAt;
}
