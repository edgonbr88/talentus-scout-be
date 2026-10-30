package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteProfileRequest {

    @NotNull(message = "El rol es obligatorio")
    private UserRole role;

    @NotBlank(message = "El número telefónico es obligatorio")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "El número telefónico debe estar en formato internacional E.164 (ej: +584121234567)")
    private String phoneNumber;

    private Boolean acceptLegalConsent;

    private String organizationName;
    private String scoutRole;
    private String linkedinUrl;
}
