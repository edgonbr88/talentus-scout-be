package com.talentus.scout.web.dto.scoutnetwork;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutNetworkFiltersResponse {
    private List<ScoutNetworkOrganizationOption> organizations;
    private List<String> countries;
    private String viewerOrganizationName;
    private boolean canFilterSameClub;
}
