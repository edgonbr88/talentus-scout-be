package com.talentus.scout.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

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
}
