package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfigResponse {
    private BigDecimal bcvRate;
    private Instant bcvLastUpdated;
    private String bankName;
    private String phoneNumber;
    private String idNumber;
    private String accountHolder;
    private List<SubscriptionPlanDto> plans;
    private String binancePayId;
    private String binanceWalletAddress;
    private String binanceNetwork;
    private String binanceQrImageUrl;
    @Builder.Default
    private Boolean pagoMovilEnabled = true;
    @Builder.Default
    private Boolean binanceManualEnabled = true;
}
