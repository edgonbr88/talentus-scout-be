package com.talentus.scout.web.dto.publicprofile;

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
public class PublicSocialLinksDto {
    private String instagramUrl;
    private String tiktokUrl;
    private String youtubeUrl;
    private String otherPlatformUrl;
    private String otherPlatformLabel;
}
