package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.BadgeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certification_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificationRequest {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "badge_type", columnDefinition = "badge_type", nullable = false)
    private BadgeType badgeType;

    @Column(name = "request_method", nullable = false, length = 30)
    private String requestMethod; // DOCUMENT_UPLOAD, TOKEN_LINK, DIRECT_ORGANIZATION

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, EXPIRED

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence_document_url", columnDefinition = "TEXT")
    private String evidenceDocumentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private User reviewedByAdmin;

    @Column(name = "target_email", length = 150)
    private String targetEmail;

    @Column(name = "target_name", length = 150)
    private String targetName;

    @Column(name = "target_organization", length = 150)
    private String targetOrganization;

    @Column(name = "token_hash", length = 255, unique = true)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "certifier_full_name", length = 150)
    private String certifierFullName;

    @Column(name = "certifier_license_id", length = 100)
    private String certifierLicenseId;

    @Column(name = "certifier_notes", columnDefinition = "TEXT")
    private String certifierNotes;

    @Column(name = "certifier_ip", length = 50)
    private String certifierIp;

    @Column(name = "certification_data_json", columnDefinition = "TEXT")
    private String certificationDataJson;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id")
    private AthleteBadge badge;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
