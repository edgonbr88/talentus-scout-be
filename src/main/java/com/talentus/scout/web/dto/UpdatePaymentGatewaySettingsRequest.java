package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePaymentGatewaySettingsRequest {

    @NotNull(message = "El estado de activación de Pago Móvil es obligatorio")
    private Boolean pagoMovilEnabled;

    @NotBlank(message = "El banco receptor de Pago Móvil es obligatorio")
    private String pagoMovilBank;

    @NotBlank(message = "El número telefónico de Pago Móvil es obligatorio")
    private String pagoMovilPhone;

    @NotBlank(message = "El documento de identidad o RIF es obligatorio")
    private String pagoMovilIdNumber;

    @NotBlank(message = "El nombre del titular de la cuenta es obligatorio")
    private String pagoMovilAccountHolder;

    @NotNull(message = "El estado de activación de Binance es obligatorio")
    private Boolean binanceManualEnabled;

    private String binancePayId;

    private String binanceWalletAddress;

    private String binanceNetwork;
}
