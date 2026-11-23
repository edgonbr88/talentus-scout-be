package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteFilterCriteria {
    private String query;
    private Integer birthYear;
    private Integer birthYearFrom;
    private Integer birthYearTo;
    private String primaryPosition;
    private String preferredFoot;
    private UUID clubId;
    private Boolean hasEuropeanPassport;
    private Boolean hasVideo;
    private String residenceCountry;
    private BigDecimal minHeightCm;
    private BigDecimal maxHeightCm;
    private BigDecimal minWeightKg;
    private BigDecimal maxWeightKg;
}
