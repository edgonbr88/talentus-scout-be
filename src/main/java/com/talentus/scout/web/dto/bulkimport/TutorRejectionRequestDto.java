package com.talentus.scout.web.dto.bulkimport;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorRejectionRequestDto {
    @NotBlank(message = "El token es obligatorio")
    private String token;

    private String reason;
}
