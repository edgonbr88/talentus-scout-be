package com.talentus.scout.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Builder.Default
    @Column(name = "price_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceUsd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays = 30;

    @Builder.Default
    @Column(name = "max_videos", nullable = false)
    private Integer maxVideos = 1;

    @Builder.Default
    @Column(name = "has_radar_views", nullable = false)
    private Boolean hasRadarViews = false;

    @Builder.Default
    @Column(name = "has_pdf_cv", nullable = false)
    private Boolean hasPdfCv = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.priceUsd == null) {
            this.priceUsd = BigDecimal.ZERO;
        }
        if (this.durationDays == null) {
            this.durationDays = 30;
        }
        if (this.maxVideos == null) {
            this.maxVideos = 1;
        }
        if (this.hasRadarViews == null) {
            this.hasRadarViews = false;
        }
        if (this.hasPdfCv == null) {
            this.hasPdfCv = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
