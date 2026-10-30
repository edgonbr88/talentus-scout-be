package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateDiscountCodeRequest {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 50, message = "El código no puede superar 50 caracteres")
    private String code;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    @NotNull(message = "El tipo de descuento es obligatorio")
    private DiscountType discountType;

    @NotNull(message = "El valor del descuento es obligatorio")
    @DecimalMin(value = "0.01", message = "El valor debe ser mayor a 0")
    private BigDecimal discountValue;

    private UUID organizationId;

    private String applicablePlanId;

    private Integer maxUses;

    @Builder.Default
    private Boolean oneUsePerAthlete = true;

    private Instant startsAt;

    private Instant expiresAt;

    @Builder.Default
    private Boolean isActive = true;
}
