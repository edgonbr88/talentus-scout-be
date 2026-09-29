package com.talentus.scout.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "athletes")
public class Athlete {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    @Column(name = "gender", nullable = false, length = 10)
    private String gender = "MALE";

    @Column(name = "primary_position", nullable = false, length = 50)
    private String primaryPosition;

    @Column(name = "secondary_position", length = 50)
    private String secondaryPosition;

    @Column(name = "preferred_foot", nullable = false, length = 20)
    private String preferredFoot;

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

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Athlete() {
    }

    public Athlete(
            User tutor,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String gender,
            String primaryPosition,
            String secondaryPosition,
            String preferredFoot,
            String nationality,
            String secondaryPassports,
            ClubOrganization currentClub,
            String federationLicenseNumber,
            BigDecimal heightCm,
            BigDecimal weightKg,
            String bio,
            String slug,
            boolean isPublic
    ) {
        this.tutor = tutor;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.gender = gender != null ? gender : "MALE";
        this.primaryPosition = primaryPosition;
        this.secondaryPosition = secondaryPosition;
        this.preferredFoot = preferredFoot;
        this.nationality = nationality != null ? nationality : "Venezolana";
        this.secondaryPassports = secondaryPassports;
        this.currentClub = currentClub;
        this.federationLicenseNumber = federationLicenseNumber;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.bio = bio;
        this.slug = slug;
        this.isPublic = isPublic;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPrimaryPosition() {
        return primaryPosition;
    }

    public void setPrimaryPosition(String primaryPosition) {
        this.primaryPosition = primaryPosition;
    }

    public String getSecondaryPosition() {
        return secondaryPosition;
    }

    public void setSecondaryPosition(String secondaryPosition) {
        this.secondaryPosition = secondaryPosition;
    }

    public String getPreferredFoot() {
        return preferredFoot;
    }

    public void setPreferredFoot(String preferredFoot) {
        this.preferredFoot = preferredFoot;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getSecondaryPassports() {
        return secondaryPassports;
    }

    public void setSecondaryPassports(String secondaryPassports) {
        this.secondaryPassports = secondaryPassports;
    }

    public ClubOrganization getCurrentClub() {
        return currentClub;
    }

    public void setCurrentClub(ClubOrganization currentClub) {
        this.currentClub = currentClub;
    }

    public String getFederationLicenseNumber() {
        return federationLicenseNumber;
    }

    public void setFederationLicenseNumber(String federationLicenseNumber) {
        this.federationLicenseNumber = federationLicenseNumber;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
