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
public class GoogleSheetImportRequestDto {
    @NotBlank(message = "El URL o ID de Google Sheets es obligatorio")
    private String sheetUrlOrId;
}
