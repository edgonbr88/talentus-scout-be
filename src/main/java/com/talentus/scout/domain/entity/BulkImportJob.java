package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.BulkImportSourceType;
import com.talentus.scout.domain.enums.BulkImportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_import_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImportJob {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scout_id", nullable = false)
    private User scout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private ClubOrganization club;

    @Column(name = "organization_name", nullable = false, length = 150)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_type", columnDefinition = "bulk_import_source_type", nullable = false)
    private BulkImportSourceType sourceType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "sheet_id", length = 255)
    private String sheetId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "bulk_import_status", nullable = false)
    private BulkImportStatus status = BulkImportStatus.PENDING;

    @Builder.Default
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Builder.Default
    @Column(name = "processed_rows", nullable = false)
    private Integer processedRows = 0;

    @Builder.Default
    @Column(name = "successful_rows", nullable = false)
    private Integer successfulRows = 0;

    @Builder.Default
    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_report_json", columnDefinition = "JSONB")
    private String errorReportJson;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
