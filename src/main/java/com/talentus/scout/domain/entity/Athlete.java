package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.LegalVerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "athletes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Athlete {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Builder.Default
    @Column(name = "gender", nullable = false, length = 10)
    private String gender = "MALE";

    @Column(name = "primary_position", nullable = false, length = 50)
    private String primaryPosition;

    @Column(name = "secondary_position", length = 50)
    private String secondaryPosition;

    @Column(name = "preferred_foot", nullable = false, length = 20)
    private String preferredFoot;

    @Builder.Default
    @Column(name = "nationality", nullable = false, length = 50)
    private String nationality = "Venezolana";

    @Column(name = "secondary_passports", length = 150)
    private String secondaryPassports;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_club_id")
    private ClubOrganization currentClub;

    @Column(name = "federation_license_number", length = 50)
    private String federationLicenseNumber;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "profile_photo_url", columnDefinition = "TEXT")
    private String profilePhotoUrl;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "identity_document_type", length = 30)
    private String identityDocumentType;

    @Column(name = "identity_document_number", length = 50)
    private String identityDocumentNumber;

    @Column(name = "identity_document_file_url", length = 500)
    private String identityDocumentFileUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "legal_verification_status", nullable = false, length = 30)
    private LegalVerificationStatus legalVerificationStatus = LegalVerificationStatus.PENDING_REVIEW;

    @Column(name = "legal_verification_notes", columnDefinition = "TEXT")
    private String legalVerificationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_verified_by")
    private User legalVerifiedBy;

    @Column(name = "legal_verified_at")
    private Instant legalVerifiedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
