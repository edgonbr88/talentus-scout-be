package com.talentus.scout.web.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPaymentSummaryResponse {
    private BigDecimal totalRevenueUsd;
    private BigDecimal totalRevenueVes;
    private long pendingAuditCount;
    private long approvedCount;
    private long rejectedCount;
    private long binancePayApprovedCount;
    private long pagoMovilApprovedCount;
}
