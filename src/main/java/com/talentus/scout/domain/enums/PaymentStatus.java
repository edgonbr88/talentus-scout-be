package com.talentus.scout.domain.enums;

public enum PaymentStatus {
    PENDING_AUDIT,
    PENDING_GATEWAY,
    APPROVED,
    REJECTED,
    REVERSED,
    AUTO_CONFIRMED
}
