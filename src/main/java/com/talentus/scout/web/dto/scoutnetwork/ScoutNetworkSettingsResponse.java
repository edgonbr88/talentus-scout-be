package com.talentus.scout.web.dto.scoutnetwork;

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
public class ScoutNetworkSettingsResponse {
    private String profilePhotoUrl;
    private String organizationName;
    private String scoutRole;
    private String country;
    private String state;
    private String linkedinUrl;
    private boolean showInNetwork;
    private boolean showWhatsappInNetwork;
    private boolean showEmailInNetwork;
}
