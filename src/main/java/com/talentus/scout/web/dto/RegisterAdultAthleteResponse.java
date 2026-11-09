package com.talentus.scout.web.dto;

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
public class RegisterAdultAthleteResponse {

    private UUID userId;
    private UUID athleteId;
    private String token;
    private String fullName;
    private String email;
    private String role;
    private String slug;
    private String message;
}
