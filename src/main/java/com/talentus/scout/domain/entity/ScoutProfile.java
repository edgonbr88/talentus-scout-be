package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scout_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutProfile {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "organization_name", nullable = false, length = 150)
    private String organizationName;

    @Column(name = "scout_role", nullable = false, length = 100)
    private String scoutRole;

    @Column(name = "credentials_document_url", nullable = false, columnDefinition = "TEXT")
    private String credentialsDocumentUrl;

    @Column(name = "linkedin_url", columnDefinition = "TEXT")
    private String linkedinUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "verification_status", columnDefinition = "scout_verification_status", nullable = false)
    private ScoutVerificationStatus verificationStatus = ScoutVerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private User reviewedByAdmin;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (verificationStatus == null) {
            verificationStatus = ScoutVerificationStatus.PENDING;
        }
    }
}
