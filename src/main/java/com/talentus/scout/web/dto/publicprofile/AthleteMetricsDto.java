package com.talentus.scout.web.dto.publicprofile;

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
public class AthleteMetricsDto {
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private boolean medicalCertified;
    private String medicalCertifierInfo;
}
