package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAuthRequest {

    @NotBlank(message = "El token de autenticación es obligatorio")
    private String token;

    private String mockEmail;
    private String mockName;
}
