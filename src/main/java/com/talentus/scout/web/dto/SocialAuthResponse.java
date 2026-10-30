package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.UserRole;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAuthResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;
    private UUID userId;
    private String email;
    private String fullName;
    private UserRole role;

    @Builder.Default
    private boolean needsProfileCompletion = false;

    private String provider;
}
