package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class CreateAthleteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede exceder 100 caracteres")
    private String lastName;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser en el pasado")
    private LocalDate birthDate;

    private String gender = "MALE";

    @NotBlank(message = "La posición principal es obligatoria")
    @Size(max = 50)
    private String primaryPosition;

    @Size(max = 50)
    private String secondaryPosition;

    @NotBlank(message = "El pie hábil es obligatorio")
    @Size(max = 20)
    private String preferredFoot;

    private String nationality = "Venezolana";

    @Size(max = 150)
    private String secondaryPassports;

    private UUID currentClubId;

    @Size(max = 50)
    private String federationLicenseNumber;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String profilePhotoUrl;

    private String bio;

    private boolean isPublic = true;

    public CreateAthleteRequest() {
    }

    public CreateAthleteRequest(String firstName, String lastName, LocalDate birthDate, String primaryPosition, String preferredFoot) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.primaryPosition = primaryPosition;
        this.preferredFoot = preferredFoot;
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

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }
}
