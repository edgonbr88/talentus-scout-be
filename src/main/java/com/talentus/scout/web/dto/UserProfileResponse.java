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
public class UserProfileResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private AccountStatus status;
    private ScoutVerificationStatus scoutVerificationStatus;
    private String rejectionReason;
    private boolean isProfileCompleted;
    private String country;
    private String state;
    private String address;
}
