package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.DiscountType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateCouponResponse {

    private boolean valid;
    private String message;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal originalAmountUsd;
    private BigDecimal discountAmountUsd;
    private BigDecimal finalAmountUsd;
    private BigDecimal finalAmountVes;
    private BigDecimal bcvExchangeRate;
    private boolean isFreePromo;
    private UUID organizationId;
    private String organizationName;
}
