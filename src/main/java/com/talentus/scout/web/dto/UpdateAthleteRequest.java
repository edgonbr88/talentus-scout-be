package com.talentus.scout.web.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAthleteRequest {

    @Size(max = 50)
    private String primaryPosition;

    @Size(max = 50)
    private String secondaryPosition;

    @Size(max = 20)
    private String preferredFoot;

    private UUID currentClubId;

    @Size(max = 50)
    private String federationLicenseNumber;

    @Size(max = 150)
    private String secondaryPassports;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String profilePhotoUrl;

    private String bio;

    private Boolean isPublic;
}
