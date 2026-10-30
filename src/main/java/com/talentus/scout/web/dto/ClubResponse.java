package com.talentus.scout.web.dto;

import com.talentus.scout.domain.entity.ClubOrganization;
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
public class ClubResponse {

    private UUID id;
    private String name;
    private String federationCode;
    private String city;
    private String state;
    private String country;
    private String logoUrl;
    private boolean verifiedOfficial;

    public ClubResponse(ClubOrganization club) {
        if (club != null) {
            this.id = club.getId();
            this.name = club.getName();
            this.federationCode = club.getFederationCode();
            this.city = club.getCity();
            this.state = club.getState();
            this.country = club.getCountry();
            this.logoUrl = club.getLogoUrl();
            this.verifiedOfficial = club.isVerifiedOfficial();
        }
    }
}
