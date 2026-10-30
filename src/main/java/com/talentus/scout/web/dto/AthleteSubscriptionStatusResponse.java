package com.talentus.scout.web.dto;

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
public class AthleteSubscriptionStatusResponse {
    private UUID athleteId;
    private String athleteName;
    private String status; // ACTIVE, PENDING_AUDIT, INACTIVE
    private String planId;
    private String planName;
    private Instant startDate;
    private Instant endDate;
    private Long daysRemaining;
    private boolean canAccessRadar;
    private boolean visibleInScoutDirectory;
}
