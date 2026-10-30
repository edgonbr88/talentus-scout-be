package com.talentus.scout.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportPagoMovilRequest {

    @NotNull(message = "El ID del atleta es obligatorio")
    private UUID athleteId;

    @NotBlank(message = "El plan es obligatorio")
    private String planId;

    @NotBlank(message = "El número de referencia es obligatorio")
    @Size(min = 4, max = 50, message = "El número de referencia debe tener entre 4 y 50 caracteres")
    private String referenceNumber;

    private LocalDate paymentDate;

    private String originBank;

    private String originPhone;

    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal amountVes;

    private BigDecimal bcvRate;

    private String discountCode;
}
