package com.talentus.scout.web.dto.admin.club;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateClubRequest {

    @NotBlank(message = "El nombre de la organización o club es obligatorio.")
    @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres.")
    private String name;

    @Size(max = 50, message = "El código federativo no puede exceder los 50 caracteres.")
    private String federationCode;

    @NotBlank(message = "La ciudad sede es obligatoria.")
    @Size(max = 100, message = "La ciudad no puede exceder los 100 caracteres.")
    private String city;

    @NotBlank(message = "El estado o provincia es obligatorio.")
    @Size(max = 100, message = "El estado no puede exceder los 100 caracteres.")
    private String state;

    @Builder.Default
    @NotBlank(message = "El país es obligatorio.")
    @Size(max = 100, message = "El país no puede exceder los 100 caracteres.")
    private String country = "Venezuela";

    private String logoUrl;

    @Builder.Default
    private boolean verifiedOfficial = false;
}
