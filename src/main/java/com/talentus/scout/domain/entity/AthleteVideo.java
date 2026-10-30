package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.VideoCategory;
import com.talentus.scout.domain.enums.VideoStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "athlete_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteVideo {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private VideoCategory category = VideoCategory.HIGHLIGHTS;

    @Column(name = "provider_video_id", nullable = false, length = 100)
    private String providerVideoId;

    @Column(name = "playback_url", columnDefinition = "TEXT")
    private String playbackUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Builder.Default
    @Column(name = "duration_seconds")
    private Integer durationSeconds = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "video_status", nullable = false)
    private VideoStatus status = VideoStatus.PENDING_UPLOAD;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 1;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public AthleteVideo(Athlete athlete, String title, VideoCategory category, String providerVideoId, Integer sortOrder) {
        this.athlete = athlete;
        this.title = title;
        this.category = category != null ? category : VideoCategory.HIGHLIGHTS;
        this.providerVideoId = providerVideoId;
        this.sortOrder = sortOrder != null ? sortOrder : 1;
        this.status = VideoStatus.PENDING_UPLOAD;
        this.durationSeconds = 0;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = VideoStatus.PENDING_UPLOAD;
        }
        if (this.category == null) {
            this.category = VideoCategory.HIGHLIGHTS;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 1;
        }
        if (this.durationSeconds == null) {
            this.durationSeconds = 0;
        }
    }
}
