package com.talentus.scout.web.dto.bulkimport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRowErrorDto {
    private int rowNumber;
    private String athleteName;
    private String reason;
    private String actionTaken; // e.g., "OMITTED_DUPLICATE", "INVALID_DATA"
}
