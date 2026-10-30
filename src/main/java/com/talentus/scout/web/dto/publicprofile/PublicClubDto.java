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
public class PublicClubDto {
    private String name;
    private String logoUrl;
    private String city;
    private String state;
    private String country;
}
