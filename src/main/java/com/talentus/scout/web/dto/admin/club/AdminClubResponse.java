package com.talentus.scout.web.dto.admin.club;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminClubResponse {
    private UUID id;
    private String name;
    private String federationCode;
    private String city;
    private String state;
    private String country;
    private String logoUrl;
    private boolean verifiedOfficial;
    private Instant createdAt;
    private long athletesCount;
    private long scoutsCount;
}
