package com.talentus.scout.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clubs_organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubOrganization {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "federation_code", length = 50)
    private String federationCode;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Builder.Default
    @Column(name = "country", nullable = false, length = 100)
    private String country = "Venezuela";

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Builder.Default
    @Column(name = "verified_official", nullable = false)
    private boolean verifiedOfficial = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

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
}
