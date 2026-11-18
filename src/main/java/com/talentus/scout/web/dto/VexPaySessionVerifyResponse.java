package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VexPaySessionVerifyResponse {
    private UUID transactionId;
    private String sessionId;
    private String sessionStatus;
    private PaymentStatus transactionStatus;
    private String paymentId;
    private String subMethod;
    private UUID athleteId;
    private String athleteName;
    private String planName;
    private boolean activated;
    private String message;
}
