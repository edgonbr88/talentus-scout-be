package com.talentus.scout.web.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBinanceOrderResponse {
    private UUID transactionId;
    private String prepayId;
    private String gatewayOrderId;
    private String checkoutUrl;
    private String qrContent;
    private Long expireTime;
    private BigDecimal amountUsd;
    private String currency; // "USDT"
}
