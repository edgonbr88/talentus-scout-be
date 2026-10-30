package com.talentus.scout.service.video;

import com.talentus.scout.service.video.model.UploadTicket;
import com.talentus.scout.service.video.model.VideoDetails;

import java.util.Map;
import java.util.UUID;

public interface VideoStreamProvider {
    UploadTicket requestUploadTicket(UUID videoId, UUID athleteId, String title);
    VideoDetails parseWebhook(String payload, Map<String, String> headers);
    String getProviderName();
}
