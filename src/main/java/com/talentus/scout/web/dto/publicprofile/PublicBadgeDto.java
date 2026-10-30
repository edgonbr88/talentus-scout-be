package com.talentus.scout.web.dto.publicprofile;

import com.talentus.scout.domain.enums.BadgeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicBadgeDto {
    private BadgeType type;
    private String title;
    private boolean verified;
    private String organizationName;
    private Instant verifiedAt;
}
