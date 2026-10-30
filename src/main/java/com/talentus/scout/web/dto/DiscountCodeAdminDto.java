package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.DiscountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountCodeAdminDto {

    private UUID id;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private UUID organizationId;
    private String organizationName;
    private String applicablePlanId;
    private String applicablePlanName;
    private Integer maxUses;
    private Integer currentUses;
    private Boolean oneUsePerAthlete;
    private Instant startsAt;
    private Instant expiresAt;
    private Boolean isActive;
    private Instant createdAt;
}
