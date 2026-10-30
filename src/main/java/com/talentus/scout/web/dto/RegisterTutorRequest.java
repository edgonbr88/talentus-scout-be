package com.talentus.scout.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterTutorRequest {

    public RegisterTutorRequest(String fullName, String email, String password, String phoneNumber, Boolean acceptLopnnaTerms) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.acceptLopnnaTerms = acceptLopnnaTerms;
        this.country = "Venezuela";
        this.state = "Distrito Capital";
    }

    @NotBlank(message = "El país es obligatorio")
    @Pattern(regexp = "^(Venezuela|Colombia|Chile|Perú|Peru|Ecuador|Bolivia)$", message = "El país debe ser uno de los autorizados: Venezuela, Colombia, Chile, Perú, Ecuador, Bolivia")
    private String country;

    @NotBlank(message = "El estado o provincia es obligatorio")
    private String state;

    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String address;

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

    @NotNull(message = "El consentimiento legal de protección de menores es obligatorio")
    @AssertTrue(message = "Debe aceptar obligatoriamente los términos de protección integral del menor y consentimiento legal")
    private Boolean acceptLopnnaTerms;

    @NotBlank(message = "El tipo de documento de identidad es obligatorio")
    @Size(max = 30)
    private String idDocumentType;

    @NotBlank(message = "El número de documento de identidad es obligatorio")
    @Size(max = 50)
    private String idDocumentNumber;

    @NotNull(message = "El archivo del documento de identidad es obligatorio")
    private MultipartFile idDocumentFile;
}
