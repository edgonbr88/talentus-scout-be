package com.talentus.scout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class UploadAthleteIdentityDocumentRequest {

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Size(max = 30)
    private String documentType;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 50)
    private String documentNumber;

    @NotNull(message = "El archivo del documento de identidad es obligatorio")
    private MultipartFile file;
}
