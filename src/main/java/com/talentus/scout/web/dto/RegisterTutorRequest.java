package com.talentus.scout.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterTutorRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String fullName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "Formato de correo electrónico inválido")
    @Size(max = 255, message = "El correo no puede exceder 255 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "El número telefónico es obligatorio")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "El número telefónico debe estar en formato internacional E.164 (ej: +584121234567)")
    private String phoneNumber;

    @NotNull(message = "El consentimiento LOPNNA es obligatorio")
    @AssertTrue(message = "Debe aceptar obligatoriamente los términos de protección de menores LOPNNA Art. 65")
    private Boolean acceptLopnnaTerms;

    public RegisterTutorRequest() {
    }

    public RegisterTutorRequest(String fullName, String email, String password, String phoneNumber, Boolean acceptLopnnaTerms) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.acceptLopnnaTerms = acceptLopnnaTerms;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean getAcceptLopnnaTerms() {
        return acceptLopnnaTerms;
    }

    public void setAcceptLopnnaTerms(Boolean acceptLopnnaTerms) {
        this.acceptLopnnaTerms = acceptLopnnaTerms;
    }
}
