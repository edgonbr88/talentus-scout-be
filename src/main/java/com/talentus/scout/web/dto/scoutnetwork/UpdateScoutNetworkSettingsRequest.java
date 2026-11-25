package com.talentus.scout.web.dto.scoutnetwork;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateScoutNetworkSettingsRequest {
    private Boolean showInNetwork;
    private Boolean showWhatsappInNetwork;
    private Boolean showEmailInNetwork;

    @Size(max = 500, message = "La URL de LinkedIn no puede exceder 500 caracteres")
    private String linkedinUrl;

    @Size(max = 100, message = "El país no puede exceder 100 caracteres")
    private String country;

    @Size(max = 100, message = "El estado/región no puede exceder 100 caracteres")
    private String state;
}
