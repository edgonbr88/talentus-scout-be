package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.AccountStatus;
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
public class RegisterTutorResponse {

    private UUID userId;
    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    @Builder.Default
    private UserRole role = UserRole.TUTOR;

    private AccountStatus status;
    private String email;
    private String fullName;
    private String message;

    public RegisterTutorResponse(UUID userId, String token, long expiresIn, String email, String fullName) {
        this.userId = userId;
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.role = UserRole.TUTOR;
        this.email = email;
        this.fullName = fullName;
    }
}
