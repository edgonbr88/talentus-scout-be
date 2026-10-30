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
public class ScoutInteractionItem {

    private String scoutName;
    private String scoutClub;
    private String scoutRole;
    private String activityType;
    private UUID athleteId;
    private String athleteName;
    private Instant timestamp;
}
