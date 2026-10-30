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
public class AthleteFilterCriteria {
    private Integer birthYear;
    private Integer birthYearFrom;
    private Integer birthYearTo;
    private String primaryPosition;
    private String preferredFoot;
    private UUID clubId;
    private Boolean hasEuropeanPassport;
    private Boolean hasVideo;
    private String residenceCountry;
}
