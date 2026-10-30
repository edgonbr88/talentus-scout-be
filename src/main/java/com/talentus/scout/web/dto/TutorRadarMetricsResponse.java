package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorRadarMetricsResponse {

    private long totalSearchAppearances;
    private long totalProfileViews;
    private long totalContactClicks;

    @Builder.Default
    private List<ScoutInteractionItem> recentInteractions = new ArrayList<>();

    @Builder.Default
    private List<AthleteRadarMetricItem> athletesMetrics = new ArrayList<>();
}
