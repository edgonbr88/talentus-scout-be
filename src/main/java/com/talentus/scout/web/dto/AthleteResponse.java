package com.talentus.scout.web.dto;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteResponse {

    private UUID id;
    private UUID tutorId;
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate birthDate;
    private int age;
    private String gender;
    private String primaryPosition;
    private String secondaryPosition;
    private String preferredFoot;
    private String nationality;
    private String secondaryPassports;
    private ClubResponse currentClub;
    private String federationLicenseNumber;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private boolean medicalCertifiedMetrics;
    private String medicalCertifierInfo;
    private String subscriptionStatus;
    private Instant subscriptionEndDate;
    private String subscriptionPlanName;
    private String profilePhotoUrl;
    private String bio;
    private String slug;
    private boolean isPublic;
    private String identityDocumentType;
    private String identityDocumentNumber;
    private String identityDocumentFileUrl;
    private boolean hasIdentityDocument;
    private LegalVerificationStatus legalVerificationStatus;
    private String legalVerificationNotes;
    private Instant legalVerifiedAt;
    private Instant createdAt;

    public AthleteResponse(Athlete athlete) {
        if (athlete != null) {
            this.id = athlete.getId();
            if (athlete.getTutor() != null) {
                this.tutorId = athlete.getTutor().getId();
            }
            this.firstName = athlete.getFirstName();
            this.lastName = athlete.getLastName();
            this.fullName = athlete.getFirstName() + " " + athlete.getLastName();
            this.birthDate = athlete.getBirthDate();
            if (athlete.getBirthDate() != null) {
                this.age = Period.between(athlete.getBirthDate(), LocalDate.now()).getYears();
            }
            this.gender = athlete.getGender();
            this.primaryPosition = athlete.getPrimaryPosition();
            this.secondaryPosition = athlete.getSecondaryPosition();
            this.preferredFoot = athlete.getPreferredFoot();
            this.nationality = athlete.getNationality();
            this.secondaryPassports = athlete.getSecondaryPassports();
            if (athlete.getCurrentClub() != null) {
                this.currentClub = new ClubResponse(athlete.getCurrentClub());
            }
            this.federationLicenseNumber = athlete.getFederationLicenseNumber();
            this.heightCm = athlete.getHeightCm();
            this.weightKg = athlete.getWeightKg();
            this.profilePhotoUrl = athlete.getProfilePhotoUrl();
            this.bio = athlete.getBio();
            this.slug = athlete.getSlug();
            this.isPublic = athlete.isPublic();
            this.identityDocumentType = athlete.getIdentityDocumentType();
            this.identityDocumentNumber = athlete.getIdentityDocumentNumber();
            this.identityDocumentFileUrl = athlete.getIdentityDocumentFileUrl();
            this.hasIdentityDocument = athlete.getIdentityDocumentFileUrl() != null && !athlete.getIdentityDocumentFileUrl().isBlank();
            this.legalVerificationStatus = athlete.getLegalVerificationStatus();
            this.legalVerificationNotes = athlete.getLegalVerificationNotes();
            this.legalVerifiedAt = athlete.getLegalVerifiedAt();
            this.createdAt = athlete.getCreatedAt();
        }
    }
}
