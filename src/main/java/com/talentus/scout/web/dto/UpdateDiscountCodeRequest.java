package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDiscountCodeRequest {

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    private DiscountType discountType;

    @DecimalMin(value = "0.01", message = "El valor debe ser mayor a 0")
    private BigDecimal discountValue;

    private UUID organizationId;

    private String applicablePlanId;

    private Integer maxUses;

    private Boolean oneUsePerAthlete;

    private Instant startsAt;

    private Instant expiresAt;

    private Boolean isActive;
}
