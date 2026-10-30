package com.talentus.scout.web.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BinanceWebhookResponse {
    private String returnCode;    // "SUCCESS" o "FAIL"
    private String returnMessage;
}
