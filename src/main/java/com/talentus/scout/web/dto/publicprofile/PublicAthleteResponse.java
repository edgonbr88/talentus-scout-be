package com.talentus.scout.web.dto.publicprofile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicAthleteResponse {
    private UUID id;
    private String slug;
    private String firstName;
    private String lastName;
    private String fullName;
    private int age;
    private LocalDate birthDate;
    private String gender;
    private String primaryPosition;
    private String secondaryPosition;
    private String preferredFoot;
    private String nationality;
    private String secondaryPassports;
    private String profilePhotoUrl;
    private String bio;
    private PublicClubDto club;
    private AthleteMetricsDto metrics;
    private List<PublicBadgeDto> badges;
    private List<PublicVideoDto> videos;
}
