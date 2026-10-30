package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discount_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountCode {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private ClubOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_plan_id")
    private SubscriptionPlan applicablePlan;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "current_uses", nullable = false)
    private Integer currentUses = 0;

    @Builder.Default
    @Column(name = "one_use_per_athlete", nullable = false)
    private Boolean oneUsePerAthlete = true;

    @Builder.Default
    @Column(name = "starts_at")
    private Instant startsAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
