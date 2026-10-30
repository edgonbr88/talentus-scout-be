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
@Table(name = "tutor_legal_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorLegalConsent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Builder.Default
    @Column(name = "accepted_lopnna_terms", nullable = false)
    private boolean acceptedLopnnaTerms = true;

    @Builder.Default
    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion = "1.0";

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "id_document_type", length = 30)
    private String idDocumentType;

    @Column(name = "id_document_number", length = 50)
    private String idDocumentNumber;

    @Column(name = "id_document_file_url", length = 500)
    private String idDocumentFileUrl;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

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
}
