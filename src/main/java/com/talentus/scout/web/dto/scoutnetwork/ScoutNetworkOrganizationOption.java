package com.talentus.scout.web.dto.scoutnetwork;

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
public class ScoutNetworkOrganizationOption {
    private String name;
    private UUID clubId;
    private String logoUrl;
    private boolean verifiedOfficial;
}
