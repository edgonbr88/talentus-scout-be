package com.talentus.scout.web.dto.scoutnetwork;

import com.talentus.scout.domain.enums.ScoutContactChannel;
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
public class ScoutNetworkContactResponse {
    private ScoutContactChannel channel;
    private String contactUrl;
    private String targetName;
    private String organizationName;
}
