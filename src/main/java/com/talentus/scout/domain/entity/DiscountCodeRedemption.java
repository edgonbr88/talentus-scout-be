package com.talentus.scout.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discount_code_redemptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountCodeRedemption {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discount_code_id", nullable = false)
    private DiscountCode discountCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransaction paymentTransaction;

    @Column(name = "original_amount_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalAmountUsd;

    @Column(name = "discounted_amount_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountedAmountUsd;

    @Column(name = "final_amount_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalAmountUsd;

    @CreationTimestamp
    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private Instant redeemedAt;
}
