package com.talentus.scout.web.dto;

import com.talentus.scout.domain.entity.Athlete;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

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
    private String profilePhotoUrl;
    private String bio;
    private String slug;
    private boolean isPublic;
    private Instant createdAt;

    public AthleteResponse() {
    }

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
            this.createdAt = athlete.getCreatedAt();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTutorId() {
        return tutorId;
    }

    public void setTutorId(UUID tutorId) {
        this.tutorId = tutorId;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
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

    public ClubResponse getCurrentClub() {
        return currentClub;
    }

    public void setCurrentClub(ClubResponse currentClub) {
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
}
