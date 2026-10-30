package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionResponse {
    private UUID id;
    private UUID athleteId;
    private String athleteName;
    private String planId;
    private String planName;
    private PaymentMethod method;
    private BigDecimal amountUsd;
    private BigDecimal amountVes;
    private BigDecimal bcvExchangeRate;
    private String referenceNumber;
    private String gatewayOrderId;
    private LocalDate paymentDate;
    private String originBank;
    private String originPhone;
    private PaymentStatus status;
    private String rejectionReason;
    private Instant createdAt;
    private Instant auditedAt;
    private String tutorName;
    private String tutorEmail;
    private String tutorPhone;
    private String discountCode;
    private BigDecimal discountAmountUsd;
}
