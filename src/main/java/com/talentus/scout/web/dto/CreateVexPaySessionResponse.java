package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVexPaySessionResponse {
    private String checkoutUrl;
    private String sessionId;
    private UUID transactionId;
    private BigDecimal amountUsd;
    private Instant expiresAt;
    private boolean livemode;
    private String status;
}
