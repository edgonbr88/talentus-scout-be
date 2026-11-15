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
@Table(name = "athlete_legal_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteLegalConsent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "accepted_terms", nullable = false)
    private boolean acceptedTerms = true;

    @Builder.Default
    @Column(name = "accepted_institutional_diffusion", nullable = false)
    private boolean acceptedInstitutionalDiffusion = true;

    @Builder.Default
    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion = "1.0";

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @PrePersist
    protected void onCreate() {
        if (acceptedAt == null) {
            acceptedAt = Instant.now();
        }
    }
}
