package com.talentus.scout.web.dto.publicprofile;

import com.talentus.scout.domain.enums.VideoCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicVideoDto {
    private UUID id;
    private String title;
    private VideoCategory category;
    private String playbackUrl;
    private String thumbnailUrl;
    private Integer durationSeconds;
}
