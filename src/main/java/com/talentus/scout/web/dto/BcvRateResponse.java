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
public class BcvRateResponse {
    private BigDecimal rate;
    private String source;
    private Instant lastUpdated;
    private boolean fallback;

    public BigDecimal getOfficialRate() {
        return rate;
    }

    public BcvRateResponse(BigDecimal rate, String source, String lastUpdated) {
        this.rate = rate;
        this.source = source;
        this.lastUpdated = Instant.now();
        this.fallback = false;
    }
}
