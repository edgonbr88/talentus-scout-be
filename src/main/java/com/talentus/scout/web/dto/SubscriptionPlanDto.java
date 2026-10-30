package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanDto {
    private String id;
    private String name;
    private BigDecimal priceUsd;
    private Integer durationDays;
    private Integer maxVideos;
    private Boolean hasRadarViews;
    private Boolean hasPdfCv;
}
