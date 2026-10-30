package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentGatewaySettingsDto {
    private UUID id;
    private Boolean pagoMovilEnabled;
    private String pagoMovilBank;
    private String pagoMovilPhone;
    private String pagoMovilIdNumber;
    private String pagoMovilAccountHolder;
    private Boolean binanceManualEnabled;
    private String binancePayId;
    private String binanceWalletAddress;
    private String binanceNetwork;
    private String binanceQrImageUrl;
    private Instant updatedAt;
}
