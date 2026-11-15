package com.talentus.scout.web.dto.bulkimport;

import com.talentus.scout.domain.enums.BulkImportSourceType;
import com.talentus.scout.domain.enums.BulkImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportJobResponseDto {
    private UUID id;
    private String organizationName;
    private BulkImportSourceType sourceType;
    private String fileName;
    private String sheetId;
    private BulkImportStatus status;
    private Integer totalRows;
    private Integer processedRows;
    private Integer successfulRows;
    private Integer failedRows;
    private String errorSummary;
    private List<BulkImportRowErrorDto> warnings;
    private Instant createdAt;
    private Instant updatedAt;
}
