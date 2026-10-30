package com.talentus.scout.service.video.model;

import com.talentus.scout.domain.enums.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoDetails {
    private String providerVideoId;
    private VideoStatus status;
    private String playbackUrl;
    private String thumbnailUrl;
    private Integer durationSeconds;
}
