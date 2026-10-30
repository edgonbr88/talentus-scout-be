package com.talentus.scout.web.dto.video;

import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.enums.VideoCategory;
import com.talentus.scout.domain.enums.VideoStatus;

import java.time.Instant;
import java.util.UUID;

public record AthleteVideoResponse(
        UUID id,
        UUID athleteId,
        String title,
        VideoCategory category,
        String providerVideoId,
        String playbackUrl,
        String thumbnailUrl,
        Integer durationSeconds,
        VideoStatus status,
        Integer sortOrder,
        Instant createdAt
) {
    public static AthleteVideoResponse fromEntity(AthleteVideo entity) {
        return new AthleteVideoResponse(
                entity.getId(),
                entity.getAthlete().getId(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getProviderVideoId(),
                entity.getPlaybackUrl(),
                entity.getThumbnailUrl(),
                entity.getDurationSeconds(),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getCreatedAt()
        );
    }
}
