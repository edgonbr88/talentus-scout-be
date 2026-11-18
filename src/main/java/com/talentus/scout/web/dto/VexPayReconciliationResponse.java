package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VexPayReconciliationResponse {
    private int checkedCount;
    private int approvedCount;
    private int rejectedCount;
    private int stillPendingCount;
    private int errorCount;
    private Instant executedAt;
}
