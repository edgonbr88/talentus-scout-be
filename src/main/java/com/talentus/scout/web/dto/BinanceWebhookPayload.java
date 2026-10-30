package com.talentus.scout.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceWebhookPayload {
    private String bizType;      // "PAY"
    private String bizId;        // merchantTradeNo o gatewayOrderId
    private String bizStatus;    // "PAY_SUCCESS", "PAY_CLOSED"
    private String data;         // payload raw o detalles
}
