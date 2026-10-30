package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "method", columnDefinition = "payment_method", nullable = false)
    private PaymentMethod method;

    @Column(name = "amount_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "amount_ves", precision = 15, scale = 2)
    private BigDecimal amountVes;

    @Column(name = "bcv_exchange_rate", precision = 10, scale = 4)
    private BigDecimal bcvExchangeRate;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "origin_bank", length = 100)
    private String originBank;

    @Column(name = "origin_phone", length = 30)
    private String originPhone;

    @Column(name = "proof_image_url", columnDefinition = "TEXT")
    private String proofImageUrl;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "raw_webhook_payload", columnDefinition = "TEXT")
    private String rawWebhookPayload;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "payment_status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING_AUDIT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audited_by_admin_id")
    private User auditedByAdmin;

    @Column(name = "audited_at")
    private Instant auditedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    @Builder.Default
    @Column(name = "discount_amount_usd", precision = 10, scale = 2)
    private BigDecimal discountAmountUsd = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = PaymentStatus.PENDING_AUDIT;
        }
        if (this.paymentDate == null) {
            this.paymentDate = LocalDate.now();
        }
    }
}
