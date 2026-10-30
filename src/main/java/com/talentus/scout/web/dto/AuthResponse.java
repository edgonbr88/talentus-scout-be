package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;
    private UUID userId;
    private String email;
    private String fullName;
    private UserRole role;

    public AuthResponse(String token, long expiresIn, UUID userId, String email, String fullName, UserRole role) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }
}
