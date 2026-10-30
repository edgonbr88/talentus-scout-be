package com.talentus.scout.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutDirectoryAthleteItem {
    private UUID id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private Integer birthYear;
    private String category;
    private String primaryPosition;
    private String secondaryPosition;
    private String preferredFoot;
    private String nationality;
    private String secondaryPassports;
    private boolean hasEuropeanPassport;
    private UUID clubId;
    private String clubName;
    private String clubLogoUrl;
    private String profilePhotoUrl;
    private String slug;
    private long verifiedBadgesCount;
    private boolean hasVideo;
    private String featuredVideoPlaybackUrl;
    private String featuredVideoThumbnailUrl;
    private String featuredVideoTitle;
    private boolean medicalCertifiedMetrics;
    private String residenceCountry;
    private Instant updatedAt;
}
