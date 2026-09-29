package com.talentus.scout.web.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class UpdateAthleteRequest {

    @Size(max = 50)
    private String primaryPosition;

    @Size(max = 50)
    private String secondaryPosition;

    @Size(max = 20)
    private String preferredFoot;

    private UUID currentClubId;

    @Size(max = 50)
    private String federationLicenseNumber;

    @Size(max = 150)
    private String secondaryPassports;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String profilePhotoUrl;

    private String bio;

    private Boolean isPublic;

    public UpdateAthleteRequest() {
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

    public UUID getCurrentClubId() {
        return currentClubId;
    }

    public void setCurrentClubId(UUID currentClubId) {
        this.currentClubId = currentClubId;
    }

    public String getFederationLicenseNumber() {
        return federationLicenseNumber;
    }

    public void setFederationLicenseNumber(String federationLicenseNumber) {
        this.federationLicenseNumber = federationLicenseNumber;
    }

    public String getSecondaryPassports() {
        return secondaryPassports;
    }

    public void setSecondaryPassports(String secondaryPassports) {
        this.secondaryPassports = secondaryPassports;
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

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean aPublic) {
        isPublic = aPublic;
    }
}
