package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportZellePaymentRequest {

    @NotNull(message = "El identificador del atleta es obligatorio.")
    private UUID athleteId;

    @NotBlank(message = "El plan de suscripción es obligatorio.")
    private String planId;

    @NotBlank(message = "El número de confirmación o referencia de Zelle es obligatorio.")
    @Size(min = 4, max = 100, message = "La referencia debe contener entre 4 y 100 caracteres.")
    private String referenceNumber;

    @NotBlank(message = "El nombre del titular de la cuenta emisora de Zelle es obligatorio.")
    @Size(max = 100, message = "El nombre del titular no debe exceder 100 caracteres.")
    private String originAccountHolder;

    @Size(max = 150, message = "El correo o teléfono emisor no debe exceder 150 caracteres.")
    private String originEmailOrPhone;

    @NotNull(message = "La fecha de pago es obligatoria.")
    private LocalDate paymentDate;

    private String discountCode;
}
