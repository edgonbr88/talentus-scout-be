package com.talentus.scout.web.dto.video;

import java.util.UUID;

public record UploadTicketResponse(
        UUID videoId,
        String directUploadUrl,
        String providerVideoId,
        long maxSizeBytes
) {}
