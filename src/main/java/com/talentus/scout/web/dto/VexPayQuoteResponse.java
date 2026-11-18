package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VexPayQuoteResponse {
    private BigDecimal usdAmount;
    private BigDecimal originalAmountUsd;
    private BigDecimal discountAmountUsd;
    private String discountCode;
    private BigDecimal rate;
    private String rateSource;
    private BigDecimal bcvRate;
    private BigDecimal vesAmount;
    private BigDecimal medianRate;
    private BigDecimal medianVesAmount;
    private Instant fetchedAt;
}
