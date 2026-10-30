package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteRadarMetricItem {

    private UUID athleteId;
    private String athleteName;
    private long searchAppearances;
    private long profileViews;
    private long contactClicks;
}
