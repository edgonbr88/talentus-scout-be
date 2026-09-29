package com.talentus.scout.web.dto;

import com.talentus.scout.domain.entity.ClubOrganization;
import java.util.UUID;

public class ClubResponse {

    private UUID id;
    private String name;
    private String federationCode;
    private String city;
    private String state;
    private String country;
    private String logoUrl;
    private boolean verifiedOfficial;

    public ClubResponse() {
    }

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFederationCode() {
        return federationCode;
    }

    public void setFederationCode(String federationCode) {
        this.federationCode = federationCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public boolean isVerifiedOfficial() {
        return verifiedOfficial;
    }

    public void setVerifiedOfficial(boolean verifiedOfficial) {
        this.verifiedOfficial = verifiedOfficial;
    }
}
