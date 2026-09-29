package com.talentus.scout.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clubs_organizations")
public class ClubOrganization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "federation_code", length = 50)
    private String federationCode;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 100)
    private String country = "Venezuela";

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "verified_official", nullable = false)
    private boolean verifiedOfficial = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public ClubOrganization() {
    }

    public ClubOrganization(String name, String federationCode, String city, String state, String country, String logoUrl, boolean verifiedOfficial) {
        this.name = name;
        this.federationCode = federationCode;
        this.city = city;
        this.state = state;
        this.country = country != null ? country : "Venezuela";
        this.logoUrl = logoUrl;
        this.verifiedOfficial = verifiedOfficial;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
