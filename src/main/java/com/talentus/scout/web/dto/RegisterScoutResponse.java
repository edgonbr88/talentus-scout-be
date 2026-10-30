package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
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
public class RegisterScoutResponse {

    private UUID scoutProfileId;
    private UUID userId;
    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    @Builder.Default
    private UserRole role = UserRole.SCOUT;

    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    @Builder.Default
    private ScoutVerificationStatus verificationStatus = ScoutVerificationStatus.PENDING;

    private String email;
    private String fullName;
    private String organizationName;
    private String scoutRole;
    private String credentialsDocumentUrl;

    @Builder.Default
    private String message = "Registro exitoso. Tu perfil se encuentra en auditoría por el equipo de Talentus Scout antes de habilitar el acceso al directorio de atletas.";
}
