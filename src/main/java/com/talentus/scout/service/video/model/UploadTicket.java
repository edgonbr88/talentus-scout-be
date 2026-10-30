package com.talentus.scout.service.video.model;

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
public class UploadTicket {
    private UUID videoId;
    private String directUploadUrl;
    private String providerVideoId;
    private long maxSizeBytes;
}
