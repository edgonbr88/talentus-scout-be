package com.talentus.scout.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tutor_legal_consents")
public class TutorLegalConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(name = "accepted_lopnna_terms", nullable = false)
    private boolean acceptedLopnnaTerms = true;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion = "1.0";

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    public TutorLegalConsent() {
    }

    public TutorLegalConsent(User tutor, boolean acceptedLopnnaTerms, String termsVersion, String ipAddress, String userAgent) {
        this.tutor = tutor;
        this.acceptedLopnnaTerms = acceptedLopnnaTerms;
        this.termsVersion = termsVersion;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.acceptedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (acceptedAt == null) {
            acceptedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getTutor() {
        return tutor;
    }

    public void setTutor(User tutor) {
        this.tutor = tutor;
    }

    public boolean isAcceptedLopnnaTerms() {
        return acceptedLopnnaTerms;
    }

    public void setAcceptedLopnnaTerms(boolean acceptedLopnnaTerms) {
        this.acceptedLopnnaTerms = acceptedLopnnaTerms;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
